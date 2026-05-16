package com.example.repo_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_reserves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReserve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "status")
    private String status; // PENDING ---> COMPLETED/CANCELLED OR FAILED
}