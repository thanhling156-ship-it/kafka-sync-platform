package com.example.event_library.events;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PayStatusEvent {
    private String orderId;
    private String userId;
    private String status; // SUCCESS hoặc FAILED
    private String message; // <--- THÊM DÒNG NÀY ĐỂ CHỨA LÝ DO
    private Double totalPrice;
}