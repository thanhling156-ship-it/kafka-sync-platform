package com.example.product_service.config;

import com.example.common.dto.UserRegistrationEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class UserEventListener {

    @KafkaListener(topics = "user-registration-topic", groupId = "product-group")
    public void handleUserRegistration(UserRegistrationEvent event) {
        System.out.println("🚀 [PRODUCT SERVICE] NHẬN TIN THÀNH CÔNG!");
        System.out.println("User ID: " + event.getUserId());
        System.out.println("Email: " + event.getEmail());
    }
}