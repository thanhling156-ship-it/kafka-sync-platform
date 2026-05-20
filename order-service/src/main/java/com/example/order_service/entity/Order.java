package com.example.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id; // Sử dụng UUID truyền từ Service

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double totalPrice;//được tính từ orderRequest

    @Column(nullable = false)
    private String status; // PENDING, CANCELLED, SUCCESS

    @Version // Ép DB phải rollback do sai version => ngăn được việc chỉ send 1 lần duy nhất
    // Phải đểlệnh save trước hàm kafka.send()
    private Long version; // Optimistic Locking - Giúp tránh lỗi race condition và đảm bảo chỉ thay đổi CANCELLED 1 lần duy nhất

    private String address;

    private String reason;

    private String number;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Helper method để kiểm tra xem đơn hàng đã ở trạng thái cuối chưa
    public boolean isFinalState() {
        return this.status.startsWith("CANCELLED") || this.status.equals("SHIPPED");
    }
}
