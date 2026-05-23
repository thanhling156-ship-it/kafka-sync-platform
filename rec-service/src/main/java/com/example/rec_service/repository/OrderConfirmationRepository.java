package com.example.rec_service.repository;

import com.example.rec_service.entity.OrderConfirmation;
import com.example.rec_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderConfirmationRepository extends JpaRepository<OrderConfirmation, String> {
}