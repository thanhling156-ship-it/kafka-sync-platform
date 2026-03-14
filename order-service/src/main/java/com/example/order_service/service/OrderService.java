package com.example.order_service.service;

import com.example.order_service.dto.OrderRequest;
import com.example.order_service.entity.Order;
import com.example.order_service.producer.OrderProducer;
import com.example.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    @Transactional
    public String createOrder(OrderRequest request) {
        // 1. Khởi tạo đơn hàng với trạng thái PENDING
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalPrice(request.getUnitPrice() * request.getQuantity())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .address(request.getAddress())
                .number(request.getNumber())
                .build();

        // 2. Lưu vào Database cục bộ của Order Service
        orderRepository.save(order);
        log.info("💾 Đã lưu Order {} vào Database với trạng thái PENDING", orderId);

        // 3. Bắn Fat Event lên Kafka topic "order-created"
        // Sử dụng orderId làm Message Key để đảm bảo các event của cùng 1 đơn hàng vào chung 1 Partition
        orderProducer.publishOrderCreated(order);
        log.info("🚀 Đã bắn Fat Event cho Order {}", orderId);

        return orderId;
    }

    @Transactional
    public void updateOrderStatus(String orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Chỉ cập nhật nếu đơn hàng chưa bị hủy trước đó (Idempotency)
        if (!order.getStatus().startsWith("CANCELLED")) {
            order.setStatus(status);
            orderRepository.save(order);
            log.info("Cập nhật trạng thái Order {} sang {}", orderId, status);
        }
    }
}
