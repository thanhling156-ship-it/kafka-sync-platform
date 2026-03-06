package com.example.identity_service.service;

import com.example.identity_service.dto.event.NotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProducerService {

    // KafkaTemplate<Key, Value>
    KafkaTemplate<String, Object> kafkaTemplate;

    public void sendNotification(NotificationEvent event) {
        log.info("Sending notification event to Kafka: {}", event);

        // "notification-topic" là tên cái phễu mà tin nhắn sẽ rơi vào
        kafkaTemplate.send("notification-topic", event);
    }
}