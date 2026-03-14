package com.example.repo_service.service;

import com.example.event_library.RepoStatusEvent;
import com.example.repo_service.entity.Product;
import com.example.event_library.OrderCreatedEvent;
import com.example.repo_service.entity.StockReserve;
import com.example.repo_service.repository.ProductRepository;
import com.example.repo_service.repository.StockReserveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RepoService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StockReserveRepository reserveRepository;


    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            // 1. Tìm sản phẩm dựa trên ProductCode từ Event
            Product product = productRepository.findByProductCode(event.getProductId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm: " + event.getProductId()));

            // 2. Kiểm tra tồn kho (Sử dụng hàm hasEnoughStock có sẵn của bạn)
            if (product.hasEnoughStock(event.getQuantity())) {

                // --- NHÁNH THÀNH CÔNG: CHỈ RESERVE ---

                // Tạo bản ghi giữ chỗ
                StockReserve reserve = StockReserve.builder()
                        .orderId(event.getOrderId())
                        .productCode(event.getProductId())
                        .quantity(event.getQuantity())
                        .status("PENDING") // Chờ Ship Service phản hồi
                        .build();

                reserveRepository.save(reserve);

                log.info("✅ Đã tạo bản ghi RESERVE cho đơn hàng: {}", event.getOrderId());

                // Bắn tin vào topic Success để Ship Service tổng hợp
                sendStatus("repo-success-topic", event.getOrderId(), "SUCCESS", "Stock reserved in table");

            } else {

                // --- NHÁNH THẤT BẠI: HẾT HÀNG ---
                log.warn("❌ HẾT HÀNG (Reserve fail) cho đơn: {}", event.getOrderId());

                // Bắn tin vào topic Fail - Ship sẽ biết và phát lệnh Fail-Fast
                sendStatus("repo-fail-topic", event.getOrderId(), "FAILED", "Out of stock");
            }

        } catch (Exception e) {
            log.error("💥 Lỗi xử lý Reserve cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("repo-fail-topic", event.getOrderId(), "FAILED", "System error: " + e.getMessage());
        }
    }

    @Transactional
    public void finalizeOrder(String orderId, String status) {
        // 1. Dùng Existence-based logic: Không thấy record thì EXIT ngay
        Optional<StockReserve> reserveOpt = reserveRepository.findByOrderId(orderId);

        if (reserveOpt.isEmpty()) {
            log.info("ℹ️ [EXIT] Không tìm thấy bản ghi giữ chỗ cho đơn {}. Bỏ qua vì đã xử lý hoặc Self-fail.", orderId);
            return;
        }

        StockReserve reserve = reserveOpt.get();

        // 2. Chỉ thực hiện TRỪ KHO THẬT khi Ship báo SUCCESS
        if ("SUCCESS".equalsIgnoreCase(status)) {
            Product product = productRepository.findByProductCode(reserve.getProductCode())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm biến mất khỏi DB: " + reserve.getProductCode()));

            log.info("🚚 [COMMIT] Trừ kho thật cho đơn {}: {} x{}", orderId, reserve.getProductCode(), reserve.getQuantity());

            product.reduceStock(reserve.getQuantity());
            productRepository.save(product); // Lưu lại thay đổi số lượng kho
        } else {
            // Trường hợp FAILED (Other-fail)
            log.warn("🔄 [ROLLBACK] Đơn hàng thất bại, chỉ xóa bản ghi giữ chỗ cho đơn: {}", orderId);
        }

        // 3. Cuối cùng, LUÔN LUÔN xóa bản ghi reserve (Dọn dẹp State)
        reserveRepository.delete(reserve);

        log.info("✅ Hoàn tất xử lý Repo cho đơn {}. Trạng thái cuối: {}", orderId, status);
    }

    @Transactional
    public void reduceStock(OrderCreatedEvent event) {
        try {
            // 1. Tìm và Khóa sản phẩm (Pessimistic Lock đã cấu hình ở Repo)
            Product product = productRepository.findByProductCode(event.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + event.getProductId()));

            // 2. IF-ELSE: Kiểm tra tồn kho ngay tại đây
            if (product.getStockQuantity() >= event.getQuantity()) {

                // --- NHÁNH THÀNH CÔNG ---
                product.setStockQuantity(product.getStockQuantity() - event.getQuantity());
                productRepository.save(product);

                log.info("✅ Giữ hàng THÀNH CÔNG cho đơn: {}", event.getOrderId());

                // Bắn tin vào topic Success - Ship chỉ việc nhặt và xác nhận
                sendStatus("repo-success-topic", event.getOrderId(), "SUCCESS", "Stock reserved");

            } else {

                // --- NHÁNH THẤT BẠI (HẾT HÀNG) ---
                log.warn("❌ HẾT HÀNG cho đơn: {}", event.getOrderId());

                // Bắn tin vào topic Fail - Ship nhặt được là biết phải hủy đơn/rollback ngay
                sendStatus("repo-fail-topic", event.getOrderId(), "FAILED", "Out of stock");
            }

        } catch (Exception e) {
            // --- NHÁNH LỖI HỆ THỐNG ---
            log.error("💥 Lỗi hệ thống Repo cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("repo-fail-topic", event.getOrderId(), "FAILED", e.getMessage());
        }
    }




    private void sendStatus(String topic, String orderId, String status, String message) {
        RepoStatusEvent event = RepoStatusEvent.builder()
                .orderId(orderId)
                .status(status)
                .message(message)
                .build();
        kafkaTemplate.send(topic, event);
    }
}