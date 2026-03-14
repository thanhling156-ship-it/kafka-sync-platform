package com.example.event_library;


import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipFailEvent {
    private String orderId;
    private String reason;
}
