package com.example.inventory_service.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public void reserveStock(String orderId, String productId, int quantity) {
        log.info("📦 Đang kiểm tra và giữ hàng cho đơn: {}", orderId);

        // Dùng câu lệnh SQL Update có điều kiện để tránh race condition (Pessimistic Locking)
        int updatedRows = inventoryRepository.reserveStock(productId, quantity);

        if (updatedRows > 0) {
            log.info("✅ Đã giữ chỗ thành công {} sản phẩm {}", quantity, productId);
            // Tiếp theo: Có thể bắn một event 'StockReservedEvent' để báo cho Payment
        } else {
            log.error("❌ Hết hàng! Không thể giữ chỗ cho sản phẩm {}", productId);
            // Tiếp theo: Bắn event 'StockFailedEvent' để Order Service hủy đơn
        }
    }
}