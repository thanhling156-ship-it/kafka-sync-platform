package com.example.order_service.service;

import com.example.order_service.constant.StatusCode;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.entity.Order;
import com.example.order_service.handler.NotificationHandler;
import com.example.order_service.producer.OrderProducer;
import com.example.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;
    private final NotificationHandler notificationHandler;
    @Autowired
    private StringRedisTemplate redisTemplate;

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
    public void cancelOrderStatus(String orderId, StatusCode statusCode) {
        try {
            // 1. Đọc dữ liệu mới nhất (bao gồm cả giá trị version hiện tại)
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

            // 2. Kiểm tra trạng thái nghiêm ngặt
            // Nếu một luồng trước đó đã đổi sang CANCELLED, các luồng sau sẽ bị chặn ngay tại đây
            if (order.getStatus().equals("PENDING")) {

                order.setStatus("CANCELLED");

                // 3. Ép Hibernate kiểm tra @Version dưới DB ngay lập tức
                orderRepository.saveAndFlush(order);

                // 4. Gửi thông báo (Chỉ luồng chiến thắng cuộc đua version mới chạm được đến dòng này)
                sendNotification(order.getUserId(), order.getId(), order.getProductId(), statusCode.getDescription());
            }

        } catch (ObjectOptimisticLockingFailureException e) {
            // 5. Bắt ngoại lệ khóa lạc quan (Xung đột Version)
            // Luồng chạy vào đây nghĩa là nó đã thua cuộc đua đồng thời.
            // Nếu chỉ là ghi xuống đơn hàng thông thường thì save là an toàn
            // Còn ở đây có thêm side action, mặc dù sẽ báo lỗi nhưng action đó đã được thực hiện
            log.warn("Đơn hàng {} đã được một tiến trình khác xử lý hủy trước đó.", orderId);
        }
    }

    @Transactional
    public void completeOrderStatus(String orderId, StatusCode statusCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
        if (order.getStatus().equals("PENDING")){
            String userId = order.getUserId();
            String productId = order.getProductId();
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            sendNotification(userId,orderId,productId,statusCode.getDescription());

            // Tạo key có format đặc thù để sau này dễ tách ID
            String redisKey = "tracking:" + userId + ":" + orderId + ":" + productId + ":" + "--- Đã vận chuyển thành công về phía bạn ---";

            // Set giá trị "IN_TRANSIT" với TTL là 10 giây (giả lập thời gian giao hàng)
            redisTemplate.opsForValue().set(redisKey, "IN_TRANSIT", 20, TimeUnit.SECONDS);

            log.info("Đang giả lập vận chuyển đến người dùng {} cho đơn {}",order.getUserId(),orderId);
        }
    }

    @Async
    public void sendNotification(String userId, String orderId, String productId, String message) {
        String notification = String.format("Đơn hàng %s gồm sản phẩm %s - Trạng thái %s",orderId,productId,message);
        notificationHandler.pushNotification(userId,notification);
    }
}
