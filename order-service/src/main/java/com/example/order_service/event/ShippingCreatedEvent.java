package com.example.order_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor

public class ShippingCreatedEvent extends BaseEvent {
    private String orderId;
    private String productId;
    private int quantity;
}