package com.example.ship_service.repository;

import com.example.ship_service.entity.ShippingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShippingRepository extends JpaRepository<ShippingSnapshot, String> {
    // Sử dụng String làm ID vì chúng ta dùng orderId làm khóa chính
}