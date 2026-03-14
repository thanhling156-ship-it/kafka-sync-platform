package com.example.repo_service.entity;

import jakarta.persistence.*;
import lombok.*;

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

    // Trong thực tế, bạn nên dùng SKU hoặc ProductCode
    // để khớp với productId từ Kafka bắn sang
    @Column(name = "product_code", nullable = false, unique = true)
    private String productCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "price")
    private Double price;

    // Helper method để kiểm tra và trừ kho
    public boolean hasEnoughStock(int requestedQuantity) {
        return this.stockQuantity >= requestedQuantity;
    }

    public void reduceStock(int quantity) {
        this.stockQuantity -= quantity;
    }
}