package com.example.ship_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_snapshots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
// Phải đồng nhất với OrderCreatedEvent
public class ShippingSnapshot {
    @Id
    private String orderId;

    // Trạng thái của 2 "công nhân"
    private String payStatus = "PENDING";  // PENDING, SUCCESS, FAILED
    private String repoStatus = "PENDING"; // PENDING, SUCCESS, FAILED

    // Dữ liệu dự phòng để thực hiện Rollback nếu cần
    private String userId;
    private Double amount;
    private String productId;
    private Integer quantity;

    @Version
    private Long version;

    public boolean isFinished() {
        return !payStatus.equals("PENDING") && !repoStatus.equals("PENDING");
    }

    public boolean isAllSuccess() {
        return payStatus.equals("SUCCESS") && repoStatus.equals("SUCCESS");
    }
}