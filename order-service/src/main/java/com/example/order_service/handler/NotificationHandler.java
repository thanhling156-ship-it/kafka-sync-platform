package com.example.order_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Server WebSocket
@Slf4j
@Component
public class NotificationHandler extends TextWebSocketHandler {
    // Lưu theo K-V : UserID - Session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Hàm thụ động
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        sessions.put(userId, session);
        System.out.println("New connection for userId: " + userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        sessions.remove(userId);
        System.out.println("Connection closed for userId: " + userId);
    }

    // Hàm chủ động
    public void pushNotification(String userId, String message) { // Đã bỏ 'throws IOException'
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                // Bắt lỗi tại đây và ghi log lại để hệ thống không bị sập
                log.error("🚨 Lỗi khi gửi tin nhắn WebSocket cho User {}: {}", userId, e.getMessage());
            }
        }
    }

    private String extractUserId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}