package com.example.identity_service.service;

import com.example.common.dto.UserRegistrationEvent;
import com.example.identity_service.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    //dùng khi cần bắn lên kafka, thường ở cuối hàm
    //UserRegistrationEvent ở project common
    public void publishUserRegistration(User user) {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFirstName() + " " + user.getLastName()) // Ghép tên
                .phoneNumber(user.getPhone())
                .address(user.getAddress())
                .createdAt(Instant.now())
                .build();

        log.info("Sending event to Kafka: {}", event);
        // "user-registration-topic" là cái phễu hứng tin
        kafkaTemplate.send("user-registration-topic", event);
    }
}
