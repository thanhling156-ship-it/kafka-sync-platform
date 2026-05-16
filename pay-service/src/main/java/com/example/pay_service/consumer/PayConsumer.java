package com.example.pay_service.consumer;

import com.example.event_library.*;
import com.example.pay_service.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PayConsumer {

    private final PayService payService;

    @KafkaListener(topics = "order-created", groupId = "pay-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("💰 [ORDER-CREATED] Nhận yêu cầu thanh toán đơn: {}", event.getOrderId());
        payService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "ship-fail", groupId = "pay-group")
    public void handleShipFail(ShipCreatedEvent event) {
        log.warn("🔄 [PAY-ROLLBACK] Nhận lệnh hoàn tiền cho đơn: {}", event.getOrderId());
        // Lưu ý: Event rollback nên mang theo userId và amount để refund chính xác
        payService.finalizeOrder(event.getOrderId(),"FAIL");
    }

    @KafkaListener(topics = "ship-success", groupId = "pay-group")
    public void handleShipSuccess(ShipCreatedEvent event) {
        log.warn("✅ [PAY-COMMIT] Nhận lệnh chốt tiền cho đơn: {}", event.getOrderId());
        // Lưu ý: Event rollback nên mang theo userId và amount để refund chính xác
        payService.finalizeOrder(event.getOrderId(),"SUCCESS");
    }
}
