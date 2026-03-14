package com.example.order_service.repository;

import com.example.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    // Tìm danh sách đơn hàng của một User (phục vụ lịch sử mua hàng)
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    // Tìm các đơn hàng theo trạng thái
    List<Order> findByStatus(String status);
}