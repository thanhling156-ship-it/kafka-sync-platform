package com.example.repo_service.repository;

import com.example.repo_service.entity.StockReserve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockReserveRepository extends JpaRepository<StockReserve, Long> {
    Optional<StockReserve> findByOrderId(String orderId);
}