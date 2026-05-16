package com.example.pay_service.repository;

import com.example.pay_service.entity.PaymentReserve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface PaymentReserveRepository extends JpaRepository<PaymentReserve, Long> {
    Optional<PaymentReserve> findByOrderId(String orderId);

    // Hàm tính tổng số tiền đang bị phong tỏa (PENDING)
    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM PaymentReserve p WHERE p.userId = :userId AND p.status = :status")
    Double sumAmountByUserIdAndStatus(@Param("userId") String userId, @Param("status") String status);
}