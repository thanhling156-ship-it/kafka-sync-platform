package com.example.event_library.events;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShipCreatedEvent {
    // 1. Định danh (Bắt buộc)
    private String orderId;

    // 2. Trạng thái quyết định (Bắt buộc)
    // Giá trị: "SUCCESS" hoặc "FAILED"
    private String status;

    // 3. Thông tin bổ trợ để đối soát (Nên có)
    private String productCode;
    private Integer quantity;
    private Double totalAmount;

    // 4. Lý do (Dùng để log hoặc thông báo cho khách hàng)
    private String message;

    // 5. Metadata (Dùng để xử lý lỗi kỹ thuật/tuần tự)
    private LocalDateTime createdAt;
}