package com.example.pay_service.repository;

import com.example.pay_service.entity.PaymentReserve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentReserveRepository extends JpaRepository<PaymentReserve, Long> {
    Optional<PaymentReserve> findByOrderId(String orderId);
}