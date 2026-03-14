package com.example.product_service.event;

import lombok.Data;

@Data
public class OrderCreatedEvent extends BaseEvent{
    private String orderId;     // CỰC KỲ QUAN TRỌNG: Để check trùng (Idempotency)
    private String userId;      // Ai mua?
    private String productId;   // Mua cái gì?
    private int quantity;       // Mua bao nhiêu?
    private double totalPrice;  // Tổng tiền (để thằng Payment trừ tiền)
}
