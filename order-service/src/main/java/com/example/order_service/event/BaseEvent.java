package com.example.order_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder // Dùng SuperBuilder để lớp con kế thừa được Builder
public abstract class BaseEvent {
    private String eventId;     // UUID duy nhất cho mỗi tin nhắn
    private long timestamp;     // Thời điểm phát sinh sự kiện (System.currentTimeMillis())
    private String eventType;   // Tên sự kiện (ORDER_CREATED, PAYMENT_FAILED, ...)
}