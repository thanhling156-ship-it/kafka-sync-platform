package com.example.pay_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private String userId;

    private Double balance;

    public boolean hasEnoughBalance(Double amount) {
        return this.balance >= amount;
    }

    public void deduct(Double amount) {
        this.balance -= amount;
    }

    public void refund(Double amount) {
        this.balance += amount;
    }
}