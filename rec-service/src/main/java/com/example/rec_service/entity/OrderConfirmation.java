package com.example.rec_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table (name = "confirmations")
@Data
public class OrderConfirmation {
    @Id
    private String recommendationId; // Dùng để liên kết recommendation và user
    private String userId; // Dùng để xác định user và gửi
}
