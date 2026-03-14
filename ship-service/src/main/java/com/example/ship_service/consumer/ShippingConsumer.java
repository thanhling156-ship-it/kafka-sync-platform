package com.example.ship_service.consumer;

import com.example.event_library.*;
import com.example.ship_service.entity.ShippingSnapshot;
import com.example.ship_service.repository.ShippingRepository;
import com.example.ship_service.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer; // IMPORT QUAN TRỌNG Ở ĐÂY

@Component
@Slf4j
@RequiredArgsConstructor
public class ShippingConsumer {

    private final ShippingRepository repository;
    private final ShippingService shippingService;

    // 1. Khởi tạo Snapshot khi có đơn hàng mới
    @KafkaListener(topics = "order-created", groupId = "ship-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("📝 Tạo Snapshot cho đơn hàng: {}", event.getOrderId());
        ShippingSnapshot sn = ShippingSnapshot.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.getTotalPrice())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .payStatus("PENDING")
                .repoStatus("PENDING")
                .build();
        repository.save(sn);
    }

    // 2. Nghe báo cáo từ Pay Service
    @KafkaListener(topics = "pay-success-topic", groupId = "ship-group")
    public void onPaySuccess(PayStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> sn.setPayStatus("SUCCESS"));
    }

    @KafkaListener(topics = "pay-fail-topic", groupId = "ship-group")
    public void onPayFail(PayStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> sn.setPayStatus("FAILED"));
    }

    // 3. Nghe báo cáo từ Repo Service
    @KafkaListener(topics = "repo-success-topic", groupId = "ship-group")
    public void onRepoSuccess(RepoStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> sn.setRepoStatus("SUCCESS"));
    }

    @KafkaListener(topics = "repo-fail-topic", groupId = "ship-group")
    public void onRepoFail(RepoStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> sn.setRepoStatus("FAILED"));
    }

    /**
     * Hàm dùng chung để cập nhật trạng thái và gọi Trọng tài phân xử
     * Consumer<ShippingSnapshot> updater: chính là đoạn logic "sn -> sn.setPayStatus(...)"
     */
    private void updateAndCheck(String orderId, Consumer<ShippingSnapshot> updater) {
        repository.findById(orderId).ifPresentOrElse(sn -> {
            // Thực hiện cập nhật status (Pay hoặc Repo)
            updater.accept(sn);

            // Lưu vào DB
            repository.save(sn);

            // Gọi Service để kiểm tra xem đã đủ bộ Success chưa để ra quyết định
            shippingService.checkAndFinalize(orderId);

        }, () -> log.error("❌ Không tìm thấy Snapshot cho đơn hàng: {}", orderId));
    }
}