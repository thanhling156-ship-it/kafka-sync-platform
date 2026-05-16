package com.example.repo_service.consumer;

import com.example.event_library.*;
import com.example.repo_service.service.RepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RepoConsumer {

    private final RepoService repoService;

    /**
     * TAI 1: NHẬN LỆNH TRỪ KHO (Từ Order Service)
     * Ngay khi đơn hàng vừa tạo, Repo nhảy vào trừ hàng luôn để giữ chỗ.
     */
    @KafkaListener(topics = "order-created", groupId = "repo-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("📦 [ORDER-CREATED] Nhận đơn hàng {}. Đang thực hiện trừ kho cho sản phẩm: {}",
                event.getOrderId(), event.getProductId());

        // Gọi service xử lý DB (Trừ stock)
        // Trong repoService.reduceStock nên có logic bắn lại repo-success/fail
        repoService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "ship-fail", groupId = "repo-group")
    public void handleShipFail(ShipCreatedEvent event) {
        log.warn("🔄 [REPO-ROLLBACK] Nhận lệnh hoàn hàng cho đơn: {}. Lý do từ trọng tài: {}",
                event.getOrderId(),event.getCreatedAt());

        // Gọi service xử lý DB (Cộng lại stock)
        repoService.finalizeOrder(event.getOrderId(),"FAIL");
    }

    @KafkaListener(topics = "ship-success", groupId = "repo-group")
    public void handleShipSuccess(ShipCreatedEvent event) {
        log.warn("🔄 [REPO-ROLLBACK] Nhận lệnh hoàn hàng cho đơn: {}. Lý do từ trọng tài: {}",
                event.getOrderId(),event.getCreatedAt());
        // Gọi service xử lý DB (Cộng lại stock)
        repoService.finalizeOrder(event.getOrderId(),"SUCCESS");
    }
}