package com.example.product_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Double price;

    private Integer quantity;

    @Version // 🛡️ Bảo vệ dữ liệu (Optimistic Locking)
    private Long version;

    private String createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        // Tự động hốt username từ cái túi UserContext bạn làm đêm qua
        this.createdBy = com.example.common.UserContext.getCurrentUser();
    }
}