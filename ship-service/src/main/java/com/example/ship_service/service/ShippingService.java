package com.example.ship_service.service;

import com.example.event_library.*;
import com.example.ship_service.entity.ShippingSnapshot;
import com.example.ship_service.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void checkAndFinalize(String orderId) {
        // 1. Tìm bản ghi trạng thái (Snapshot)
        ShippingSnapshot sn = repository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Snapshot not found for order: " + orderId));

        // 2. Kiểm tra xem đã nhận đủ event từ Pay và Repo chưa
        // Giả định hàm isFinished() check: payStatus != null && repoStatus != null
        if (!sn.isFinished()) {
            log.info("⏳ Đơn hàng {}: Đang chờ đủ dữ liệu (Pay: {}, Repo: {})",
                    orderId, sn.getPayStatus(), sn.getRepoStatus());
            return;
        }

        log.info("⚖️ [DECISION] Đã đủ dữ liệu cho đơn: {}. Kết quả: Pay={}, Repo={}",
                orderId, sn.getPayStatus(), sn.getRepoStatus());

        // 3. Quyết định trạng thái cuối cùng
        String finalStatus;
        String finalMessage;

        if (sn.isAllSuccess()) {
            finalStatus = "SUCCESS";
            finalMessage = "Đơn hàng đã sẵn sàng giao.";
            log.info("✅ ĐƠN HÀNG ĐÃ ĐƯỢC TẠO THÀNH CÔNG VỚI ID: {}", orderId);
            sendStatus("ship-success", orderId, finalStatus, finalMessage);
        } else {
            finalStatus = "FAILED";
            finalMessage = "Đơn hàng thất bại do: " +
                    ("FAILED".equals(sn.getPayStatus()) ? "Lỗi thanh toán. " : "") +
                    ("FAILED".equals(sn.getRepoStatus()) ? "Lỗi kho bãi." : "");
            log.warn("❌ ĐƠN HÀNG THẤT BẠI VỚI ID: {}. Lý do: {}", orderId, finalMessage);
            sendStatus("ship-fail", orderId, finalStatus, finalMessage);
        }

    }

    private void sendStatus(String topic, String orderId, String status, String message) {
        // Lưu ý: Đổi RepoStatusEvent thành ShipCreatedEvent cho đúng kiểu dữ liệu
        ShipCreatedEvent event = ShipCreatedEvent.builder()
                .orderId(orderId) // Ép kiểu nếu OrderId trong Event là Long
                .status(status)
                .message(message)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        // kafkaTemplate.send trả về một CompletableFuture
        var future = kafkaTemplate.send(topic, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // THÀNH CÔNG
                log.info("🚀 Gửi tin nhắn THÀNH CÔNG tới topic [{}]. Offset: {}",
                        topic, result.getRecordMetadata().offset());
                // Phải thành công thì mới in
                log.info("🚀 Đã phát event ship-created tới topic {}: Status={}", topic, status);
            } else {
                // THẤT BẠI
                log.error("💥 Gửi tin nhắn THẤT BẠI tới topic [{}]. Lý do: {}",
                        topic, ex.getMessage());
            }
        });
    }
}
