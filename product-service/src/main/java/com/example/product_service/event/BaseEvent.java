package com.example.product_service.event;


import lombok.Data;

@Data
public abstract class BaseEvent {
    private String eventId;     // UUID duy nhất cho mỗi tin nhắn
    private String eventType;   // VD: ORDER_CREATED, PAYMENT_FAILED
    private long createdAt;     // Thời điểm bắn tin (Timestamp)
    private int version;        // Phiên bản dữ liệu (để chống ghi đè dữ liệu cũ)


    // Getters/Setters...
}