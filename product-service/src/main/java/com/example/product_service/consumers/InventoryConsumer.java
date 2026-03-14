package com.example.product_service.consumers;

import com.example.product_service.event.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryConsumer {

    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        inventoryService.reserveStock(
                event.getProductId(),
                event.getQuantity()
        );
    }

    @KafkaListener(topics = "payment-failed", groupId = "inventory-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        inventoryService.releaseStock(
                event.getProductId(),
                event.getQuantity()
        );
    }

    @KafkaListener(topics = "payment-succeeded", groupId = "inventory-group")
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        inventoryService.confirmStock(
                event.getProductId(),
                event.getQuantity()
        );
    }
}
