package com.example.identity_service.service;

import com.example.common.dto.UserRegistrationEvent;
import com.example.identity_service.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistration(User user) {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();

        log.info("Sending event to Kafka: {}", event);
        // "user-registration-topic" là cái phễu hứng tin
        kafkaTemplate.send("user-registration-topic", event);
    }
}
