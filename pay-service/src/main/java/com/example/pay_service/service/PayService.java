package com.example.pay_service.service;

import com.example.event_library.OrderCreatedEvent;
import com.example.event_library.PayStatusEvent;
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
        try {
            // 1. Tìm người dùng dựa trên UserId từ Event
            User user = userRepository.findByUserId(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + event.getUserId()));

            // 2. Kiểm tra số dư (Sử dụng hàm hasEnoughBalance có sẵn của bạn)
            if (user.hasEnoughBalance(event.getTotalPrice())) {

                // --- NHÁNH THÀNH CÔNG: CHỈ RESERVE (PHONG TỎA TIỀN) ---

                // Tạo bản ghi giữ tiền
                PaymentReserve reserve = PaymentReserve.builder()
                        .orderId(event.getOrderId())
                        .userId(event.getUserId())
                        .amount(event.getTotalPrice())
                        .status("PENDING") // Chờ Ship Service phản hồi
                        .build();

                paymentReserveRepository.save(reserve);

                log.info("✅ Đã tạo bản ghi RESERVE (Phong tỏa tiền) cho đơn hàng: {}", event.getOrderId());

                // Bắn tin vào topic Success để Ship Service tổng hợp
                sendStatus("pay-success-topic", event.getOrderId(), "SUCCESS", "Payment reserved in table");

            } else {

                // --- NHÁNH THẤT BẠI: KHÔNG ĐỦ TIỀN ---
                log.warn("❌ KHÔNG ĐỦ SỐ DƯ (Reserve fail) cho đơn: {}", event.getOrderId());

                // Bắn tin vào topic Fail - Ship sẽ biết và phát lệnh Fail-Fast
                sendStatus("pay-fail-topic", event.getOrderId(), "FAILED", "Insufficient balance");
            }

        } catch (Exception e) {
            log.error("💥 Lỗi xử lý Reserve tiền cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("pay-fail-topic", event.getOrderId(), "FAILED", "System error: " + e.getMessage());
        }
    }

    @Transactional
    public void finalizeOrder(String orderId, String status) {
        // 1. Kiểm tra sự tồn tại (Existence-based)
        // Nếu không tìm thấy bản ghi phong tỏa, nghĩa là mình đã Self-fail hoặc đã xử lý rồi.
        Optional<PaymentReserve> reserveOpt = paymentReserveRepository.findByOrderId(orderId);

        if (reserveOpt.isEmpty()) {
            log.info("ℹ️ [IDEMPOTENT] Không tìm thấy bản ghi phong tỏa cho đơn: {}. Bỏ qua.", orderId);
            return;
        }

        PaymentReserve reserve = reserveOpt.get();

        // 2. Xử lý dựa trên phán quyết của Ship
        if ("SUCCESS".equalsIgnoreCase(status)) {
            // TRƯỜNG HỢP THÀNH CÔNG: Trừ tiền thật từ tài khoản User
            log.info("💰 [COMMIT] Đơn hàng thành công. Đang trừ tiền thật cho User: {} - Số tiền: {}",
                    reserve.getUserId(), reserve.getAmount());

            User user = userRepository.findByUserId(reserve.getUserId())
                    .orElseThrow(() -> new RuntimeException("❌ Lỗi nghiêm trọng: User không tồn tại để trừ tiền!"));

            user.setBalance(user.getBalance() - reserve.getAmount());
            userRepository.save(user);

        } else {
            // TRƯỜNG HỢP THẤT BẠI: Chỉ cần giải tỏa (vì tiền vẫn nằm trong túi User, mình mới chỉ phong tỏa trên giấy tờ)
            log.warn("🔄 [ROLLBACK] Đơn hàng thất bại. Giải tỏa số tiền phong tỏa: {}", reserve.getAmount());
        }

        // 3. Xóa bản ghi phong tỏa (Dọn dẹp State)
        paymentReserveRepository.delete(reserve);

        log.info("✅ Đã hoàn tất xử lý thanh toán cho đơn: {}. Trạng thái: {}", orderId, status);
    }

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        try {
            User user = userRepository.findByUserId(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.hasEnoughBalance(event.getTotalPrice())) {
                // --- THÀNH CÔNG ---
                user.deduct(event.getTotalPrice());
                userRepository.save(user);

                log.info("💰 Đã trừ tiền đơn: {}. Số dư còn lại: {}", event.getOrderId(), user.getBalance());
                sendStatus("pay-success-topic", event.getOrderId(), "SUCCESS", "Payment successful");
            } else {
                // --- THẤT BẠI (HẾT TIỀN) ---
                log.warn("❌ Không đủ tiền cho đơn: {}", event.getOrderId());
                sendStatus("pay-fail-topic", event.getOrderId(), "FAILED", "Insufficient balance");
            }
        } catch (Exception e) {
            log.error("💥 Lỗi thanh toán đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("pay-fail-topic", event.getOrderId(), "FAILED", e.getMessage());
        }
    }

    @Transactional
    public void refundPayment(String orderId, Double amount, String userId) {
        log.info("🔄 Hoàn tiền cho đơn: {} - User: {}", orderId, userId);
        userRepository.findByUserId(userId).ifPresent(user -> {
            user.refund(amount);
            userRepository.save(user);
        });
    }

    private void sendStatus(String topic, String orderId, String status, String message) {
        PayStatusEvent statusEvent = PayStatusEvent.builder()
                .orderId(orderId)
                .status(status)
                .message(message)
                .build();
        kafkaTemplate.send(topic, statusEvent);
    }
}