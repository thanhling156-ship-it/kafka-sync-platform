package com.example.event_library;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private String productId; // ID hoặc mã sản phẩm để trừ kho
    private Integer quantity;  // Số lượng khách đặt
    private Double totalPrice;
    private String address;    // Thông tin này Repo có thể không dùng nhưng vẫn có trong Fat Event
    private String phone;
}