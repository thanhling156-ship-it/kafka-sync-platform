package com.example.order_service.producer;

import com.example.event_library.events.OrderCreatedEvent;
import com.example.event_library.topics.EventTopics;
import com.example.order_service.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.example.event_library.topics.EventTopics.ORDER_CREATED;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Chuyển đổi từ Entity Order sang Fat Event và bắn lên Kafka
     */
    public void publishOrderCreated(Order order) {
        // 1. Đóng gói Fat Event từ thông tin của Order Entity
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice())
                .address(order.getAddress())
                .build();

        log.info("📡 Đang chuẩn bị bắn Fat Event cho đơn hàng: {}", order.getId());

        // 2. Gửi đi với Key là OrderId
        // Việc dùng OrderId làm Key cực kỳ quan trọng để các event sau này
        // (như Payment thành công) đi đúng vào cùng 1 Partition.
        kafkaTemplate.send(ORDER_CREATED, order.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ Fat Event đã lên kệ Kafka! OrderId: {} | Partition: {}",
                                order.getId(),
                                result.getRecordMetadata().partition());
                    } else {
                        log.error("❌ Bắn tin thất bại cho OrderId: {}. Lỗi: {}",
                                order.getId(), ex.getMessage());
                        // Tips: Ở đây bạn có thể lưu vào bảng 'failed_events' để gửi lại sau
                    }
                });
    }
}
