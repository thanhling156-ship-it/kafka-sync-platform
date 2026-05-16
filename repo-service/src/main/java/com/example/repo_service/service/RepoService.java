package com.example.repo_service.service;

import com.example.event_library.RepoStatusEvent;
import com.example.repo_service.entity.Product;
import com.example.event_library.OrderCreatedEvent;
import com.example.repo_service.entity.StockReserve;
import com.example.repo_service.repository.ProductRepository;
import com.example.repo_service.repository.StockReserveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
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
    private final StockReserveRepository stockReserveRepository;


    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        /*
        public class OrderCreatedEvent {
            private String orderId;
            private String userId;
            private String productId; // ID hoặc mã sản phẩm để trừ kho
            private Integer quantity;  // Số lượng khách đặt
            private Double totalPrice; // Check để xem có lệch giá hiện tại với repo không <=> quantity * price ?= totalPrice
            private String address;    // Thông tin này Repo có thể không dùng nhưng vẫn có trong Fat Event
            private String phone;
        }
         */
        try {
            // 1. Tìm sản phẩm dựa trên ProductCode từ Event
            Product product = productRepository.findByProductCode(event.getProductId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm: " + event.getProductId()));
            long totalQuantityForOrder = stockReserveRepository.sumQuantityByProductCodeAndStatus(event.getProductId(),"PENDING");
            // 2. Kiểm tra tồn kho (Sử dụng hàm hasEnoughStock có sẵn)
            StockReserve reserve = StockReserve.builder()
                    .orderId(event.getOrderId())
                    .productCode(event.getProductId())
                    .quantity(event.getQuantity())
                    .build();
            if (product.hasEnoughStock(event.getQuantity()+totalQuantityForOrder)) {
                // --- NHÁNH THÀNH CÔNG: CHỈ RESERVE HÀNG---
                reserve.setStatus("PENDING");
                log.info("✅ Đã tạo bản ghi RESERVE cho đơn hàng: {}", event.getOrderId());
                // Bắn tin vào topic Success để Ship Service tổng hợp
                sendStatus("repo-success-topic", reserve, "In Stock");
            } else {
                // --- NHÁNH THẤT BẠI: HẾT HÀNG ---
                reserve.setStatus("FAILED");
                log.warn("❌ HẾT HÀNG (Reserve fail) cho đơn: {}", event.getOrderId());
                // Bắn tin vào topic Fail để Ship Service tổng hợp
                sendStatus("repo-fail-topic", reserve, "Out of Stock");
            }
            // Tạo bản ghi giữ chỗ
            stockReserveRepository.save(reserve);
        } catch (Exception e) {
            log.error("💥 Lỗi xử lý Reserve hàng cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("repo-fail-topic", null, "System error: " + e.getMessage());
        }
    }

    private void sendStatus(String topic, StockReserve reserve, String message) {
        /*
        public class RepoStatusEvent {
            private String orderId;
            private String status; // SUCCESS hoặc FAILED
            private String message; // <--- THÊM DÒNG NÀY ĐỂ CHỨA LÝ DO
            private String productId;
            private Integer quantity;
        }
         */
        RepoStatusEvent event = RepoStatusEvent.builder()
                .orderId(reserve.getOrderId())
                .status(reserve.getStatus())
                .message(message)
                .productId(reserve.getProductCode())
                .quantity(reserve.getQuantity())
                .build();
        kafkaTemplate.send(topic, reserve.getOrderId() ,event);
    }

    @Transactional
    public void finalizeOrder(String orderId, String status) {
        // 1. Kiểm tra sự tồn tại của bản ghi phong tỏa
        Optional<StockReserve> reserveOpt = stockReserveRepository.findByOrderId(orderId);
        if (reserveOpt.isEmpty()) {
            log.info("ℹ️ [IDEMPOTENT] Không tìm thấy bản ghi cho đơn: {}. Bỏ qua.", orderId);
            return;
        }

        StockReserve reserve = reserveOpt.get();
        String stateReserve = reserve.getStatus();

        // 2. KIỂM TRA PENDING TRƯỚC (Nếu không phải PENDING thì dừng luôn)
        if (!stateReserve.equalsIgnoreCase("PENDING")) {
            log.info("ℹ️ [IDEMPOTENT] Đơn hàng {} đã được xử lý hoặc hủy bỏ từ trước (Trạng thái hiện tại: {}). Bỏ qua.", orderId, stateReserve);
            return;
        }

        // 3. Chỉ lấy thông tin User khi trạng thái hợp lệ (Tiết kiệm được 1 lần truy vấn DB nếu đơn đã xử lý)
        Product product = productRepository.findByProductCode(reserve.getProductCode())
                .orElseThrow(() -> new RuntimeException("❌ Lỗi nghiêm trọng: Không tìm thấy Product ID: " + reserve.getProductCode()));

        // 4. Xử lý nghiệp vụ khi trạng thái chắc chắn là PENDING
        if (status.equalsIgnoreCase("success")) {
            // TRƯỜNG HỢP THÀNH CÔNG: Trừ hàng thật và chuyển sang COMPLETED
            product.setStockQuantity(product.getStockQuantity() - reserve.getQuantity());
            reserve.setStatus("COMPLETED");
            log.info("🍔 [COMMIT] Đơn hàng {} thành công. Đã trừ hàng thật.", orderId);
        } else {
            // TRƯỜNG HỢP THẤT BẠI: Chỉ chuyển sang CANCELLED để giải tỏa
            reserve.setStatus("CANCELLED");
            log.warn("🔄 [ROLLBACK] Đơn hàng {} thất bại. Đã cập nhật trạng thái bản ghi SẢN PHẨM ---> CANCELLED", orderId);
        }
        productRepository.save(product);
        stockReserveRepository.save(reserve);
    }
}