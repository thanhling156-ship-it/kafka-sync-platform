package com.example.repo_service.repository;

import com.example.repo_service.entity.StockReserve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockReserveRepository extends JpaRepository<StockReserve, Long> {
    Optional<StockReserve> findByOrderId(String orderId);
    @Query("SELECT COALESCE(SUM(p.quantity), 0L) FROM StockReserve p WHERE p.productCode = :productCode AND p.status = :status")
    long sumQuantityByProductCodeAndStatus(@Param("productCode") String productCode, @Param("status") String status);
}