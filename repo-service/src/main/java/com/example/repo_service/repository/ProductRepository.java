package com.example.repo_service.repository;

import com.example.repo_service.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Tìm sản phẩm theo mã (product_code)
     * dùng để map với productId từ Kafka bắn sang.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findByProductCode(String productCode);
}