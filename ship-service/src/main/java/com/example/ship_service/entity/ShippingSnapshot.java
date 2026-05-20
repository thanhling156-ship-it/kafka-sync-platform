package com.example.ship_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_snapshots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
// Phải đồng nhất với OrderCreatedEvent
public class ShippingSnapshot {
    @Id // Đảm bảo tính duy nhất
    private String orderId;

    // Trạng thái của 2 event
    private String payStatus = "PENDING";  // PENDING, SUCCESS, FAILED
    private String repoStatus = "PENDING"; // PENDING, SUCCESS, FAILED

    // Dữ liệu dự phòng để thực hiện Rollback nếu cần
    private String userId;
    private Double amount;
    private String productId;
    private Integer quantity;

    private Integer condition = 1;

    private boolean flagFail;

    /*
    @Version
    private Long version;
    Version đảm bảo phiên, kiểu 1 đợt phát số ra thì chỉ có 1 lần được thay đổi
    => Vé dùng 1 lần, cần refresh lại
    Một dữ liệu đúng, không phải chỉ cần đúng định dạng hay hợp lệ về logic, mà nó còn phải đúng về mặt thời điểm (Ngữ cảnh thời gian)
    Tính tươi và cố định của 1 phiên dữ liệu
    mà đã là aggregator, tức là bị động, tự đi nhặt các mảnh ghép dữ liệu để tiến tới completeness, còn version là chủ động cái row đó phát số ra
    => Dùng version ở aggregator là SAI
     */

    public boolean isFinished() {
        return condition == 2;
    }

    public boolean isAllSuccess() {
        return !flagFail;
    }
}