package com.example.rec_service.config;

import com.example.rec_service.handler.NotificationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationHandler notificationHandler;   // ← đúng kiểu này

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/orders/**")  // dùng /** thay vì /*
                .setAllowedOrigins("*");   // Cho phép tất cả origin (dev)
        // .setAllowedOriginPatterns("*") // cách mới hơn
    }
}
