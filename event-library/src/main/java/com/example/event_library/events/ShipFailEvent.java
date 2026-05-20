package com.example.event_library.events;


import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipFailEvent {
    private String orderId;
    private String reason;
}
