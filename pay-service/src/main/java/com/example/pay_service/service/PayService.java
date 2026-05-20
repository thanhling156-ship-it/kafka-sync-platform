package com.example.pay_service.service;

import com.example.event_library.events.PayStatusEvent;
import com.example.event_library.events.OrderCreatedEvent;
import com.example.pay_service.entity.PaymentReserve;
import com.example.pay_service.entity.User;
import com.example.pay_service.repository.PaymentReserveRepository;
import com.example.pay_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentReserveRepository paymentReserveRepository;

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        /*
        public class OrderCreatedEvent {
            private String orderId;
            private String userId;
            private String productId; // ID hoặc mã sản phẩm để trừ kho
            private Integer quantity;  // Số lượng khách đặt
            private Double totalPrice;
            private String address;    // Thông tin này Repo có thể không dùng nhưng vẫn có trong Fat Event
            private String phone;
        }
         */
        try {
            // 1. Tìm người dùng dựa trên UserId từ Event
            User user = userRepository.findByUserId(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + event.getUserId()));
            Double totalPendingForUser = paymentReserveRepository.sumAmountByUserIdAndStatus(event.getUserId(), "PENDING");
            // 2. Kiểm tra số dư (Sử dụng hàm hasEnoughBalance có sẵn)
            PaymentReserve reserve = PaymentReserve.builder()
                    .orderId(event.getOrderId())
                    .userId(event.getUserId())
                    .amount(event.getTotalPrice())
                    .build();
            if (user.hasEnoughBalance(event.getTotalPrice()+totalPendingForUser)) {
                // --- NHÁNH THÀNH CÔNG: CHỈ RESERVE TIỀN---
                reserve.setStatus("PENDING");
                log.info("✅ Đã tạo bản ghi RESERVE cho đơn hàng: {}", event.getOrderId());
                // Bắn tin vào topic Success để Ship Service tổng hợp
                sendStatus("pay-success", reserve, "Enough Money");
            } else {
                // --- NHÁNH THẤT BẠI: HẾT TIỀN---
                reserve.setStatus("FAILED");
                log.warn("❌ HẾT TIỀN (Reserve fail) cho đơn: {}", event.getOrderId());
                // Bắn tin vào topic Fail để Ship Service tổng hợp
                sendStatus("pay-fail", reserve, "Out of Money");
            }
            // Tạo bản ghi giữ chỗ
            paymentReserveRepository.save(reserve);
        } catch (Exception e) {
            log.error("💥 Lỗi xử lý Reserve tiền cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("pay-fail", null, "System error: " + e.getMessage());
        }
    }

    private void sendStatus(String topic, PaymentReserve reserve, String message) {
        /*
        public class PayStatusEvent {
            private String orderId;
            private String userId;
            private String status; // SUCCESS hoặc FAILED
            private String message; // <--- THÊM DÒNG NÀY ĐỂ CHỨA LÝ DO
            private Double totalPrice;
        }
         */
        PayStatusEvent statusEvent = PayStatusEvent.builder()
                .orderId(reserve.getOrderId())
                .userId(reserve.getUserId())
                .status(reserve.getStatus())
                .message(message)
                .totalPrice(reserve.getAmount())
                .build();
        kafkaTemplate.send(topic, reserve.getOrderId() ,statusEvent);
    }

    @Transactional
    public void finalizeOrder(String orderId, String status) {

        // 1. Kiểm tra sự tồn tại của bản ghi phong tỏa
        Optional<PaymentReserve> reserveOpt = paymentReserveRepository.findByOrderId(orderId);
        if (reserveOpt.isEmpty()) {
            log.info("ℹ️ [IDEMPOTENT] Không tìm thấy bản ghi cho đơn: {}. Bỏ qua.", orderId);
            return;
        }

        PaymentReserve reserve = reserveOpt.get();
        String stateReserve = reserve.getStatus();

        // 2. KIỂM TRA PENDING TRƯỚC (Nếu không phải PENDING thì dừng luôn)
        if (!stateReserve.equalsIgnoreCase("PENDING")) {
            log.info("ℹ️ [IDEMPOTENT] Đơn hàng {} đã được xử lý hoặc hủy bỏ từ trước (Trạng thái hiện tại: {}). Bỏ qua.", orderId, stateReserve);
            return;
        }

        // 3. Chỉ lấy thông tin User khi trạng thái hợp lệ (Tiết kiệm được 1 lần truy vấn DB nếu đơn đã xử lý)
        User user = userRepository.findByUserId(reserve.getUserId())
                .orElseThrow(() -> new RuntimeException("❌ Lỗi nghiêm trọng: Không tìm thấy User ID: " + reserve.getUserId()));

        // 4. Xử lý nghiệp vụ khi trạng thái chắc chắn là PENDING
        if (status.equalsIgnoreCase("success")) {
            // TRƯỜNG HỢP THÀNH CÔNG: Trừ tiền thật và chuyển sang COMPLETED
            user.setBalance(user.getBalance() - reserve.getAmount());
            reserve.setStatus("COMPLETED");
            log.info("💰 [COMMIT] Đơn hàng {} thành công. Đã trừ tiền thật.", orderId);
        } else {
            // TRƯỜNG HỢP THẤT BẠI: Chỉ chuyển sang CANCELLED để giải tỏa
            reserve.setStatus("CANCELLED");
            log.warn("🔄 [ROLLBACK] Đơn hàng {} thất bại. Đã cập nhật trạng thái bản ghi THANH TOÁN ---> CANCELLED", orderId);
        }
        userRepository.save(user);
        paymentReserveRepository.save(reserve);
    }
}