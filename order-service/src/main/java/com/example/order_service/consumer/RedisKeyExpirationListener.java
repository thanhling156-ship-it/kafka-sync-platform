package com.example.order_service.consumer;

import com.example.order_service.service.OrderService;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {
    private OrderService orderService;

    private StringRedisTemplate redisTemplate;

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer,
                                      OrderService orderService,
                                      StringRedisTemplate redisTemplate) {
        super(listenerContainer); // Bắt buộc gọi super này
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
    }
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString(); // Giả sử nhận được: "tracking:101:5050:p123:CREATED"

        // Kiểm tra xem key có đúng định dạng không
        if (expiredKey.startsWith("tracking:")) {
            // Tách chuỗi bằng dấu ":"
            String[] parts = expiredKey.split(":");

            // Cấu trúc: [0]tracking, [1]userId, [2]orderId, [3]productId, [4]status
            if (parts.length == 5) {
                String userId = parts[1];
                String orderId = parts[2];
                String productId = parts[3];
                String status = parts[4];

                orderService.sendNotification(userId,orderId,productId,status);
            }
        }
    }
}