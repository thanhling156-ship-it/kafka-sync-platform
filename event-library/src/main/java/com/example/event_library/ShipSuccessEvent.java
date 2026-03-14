package com.example.event_library;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipSuccessEvent {
    private String orderId;
    private String trackingCode; // Mã vận đơn giả lập
}