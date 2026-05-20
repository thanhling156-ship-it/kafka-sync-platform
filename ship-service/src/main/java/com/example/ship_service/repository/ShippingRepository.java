package com.example.ship_service.repository;

import com.example.ship_service.entity.ShippingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ShippingRepository extends JpaRepository<ShippingSnapshot, String> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "INSERT INTO shipping_snapshots " +
            "(order_id, pay_status, repo_status, user_id, amount, product_id, quantity, condition, flag_fail) " +
            "VALUES " +
            "(:#{#snapshot.orderId}, :#{#snapshot.payStatus}, :#{#snapshot.repoStatus}, " +
            ":#{#snapshot.userId}, :#{#snapshot.amount}, :#{#snapshot.productId}, " +
            ":#{#snapshot.quantity}, :#{#snapshot.condition}, :#{#snapshot.flagFail}) " +
            "ON CONFLICT (order_id) DO UPDATE SET " +
            "pay_status = CASE WHEN EXCLUDED.pay_status <> 'PENDING' THEN EXCLUDED.pay_status ELSE shipping_snapshots.pay_status END, " +
            "repo_status = CASE WHEN EXCLUDED.repo_status <> 'PENDING' THEN EXCLUDED.repo_status ELSE shipping_snapshots.repo_status END, " +
            "user_id = COALESCE(shipping_snapshots.user_id, EXCLUDED.user_id), " +
            "amount = COALESCE(shipping_snapshots.amount, EXCLUDED.amount), " +
            "product_id = COALESCE(shipping_snapshots.product_id, EXCLUDED.product_id), " +
            "quantity = COALESCE(shipping_snapshots.quantity, EXCLUDED.quantity), " +
                   "condition = shipping_snapshots.condition + 1, " +
                           "flag_fail = (shipping_snapshots.flag_fail OR EXCLUDED.flag_fail)",
    nativeQuery = true)
    void upsertAndEnrich(@Param("snapshot") ShippingSnapshot snapshot);
}