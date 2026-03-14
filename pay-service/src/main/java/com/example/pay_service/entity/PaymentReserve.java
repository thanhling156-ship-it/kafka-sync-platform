package com.example.pay_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_reserves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReserve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId; // Khớp với userId (String) trong bảng User của bạn

    @Column(name = "amount", nullable = false)
    private Double amount;

    private String status; // PENDING, COMPLETED, CANCELLED

    
}
