package com.example.rec_service.repository;

import com.example.rec_service.entity.Product;
import com.example.rec_service.entity.ProductProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    @Query(value = """
    SELECT p2.product_id AS productId, p2.category AS category 
    FROM products p1
    JOIN products p2 ON p1.product_id = :productId
    WHERE p2.product_id != :productId
    ORDER BY p1.embedding <=> p2.embedding
    LIMIT 3
    """, nativeQuery = true)
    List<ProductProjection> findNearestByProductId(@Param("productId") String productId);
}