package com.example.order_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);

        // GIẢI PHÁP 1: Bọc session bằng Decorator để đảm bảo gửi tin bất đồng bộ an toàn (Thread-safe)
        // Tham số: session, thời gian chờ tối đa (ms), dung lượng bộ đệm tối đa (bytes)
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);

        sessions.put(userId, safeSession);

        // GIẢI PHÁP 2: Thay System.out bằng log.info (Không block luồng CPU)
        log.info("New connection established for userId: {}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        sessions.remove(userId);
        log.info("Connection closed for userId: {}, Status: {}", userId, status);
    }

    // Hàm chủ động push notification từ Kafka hoặc Redis Listener
    public void pushNotification(String userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // Nhờ có Decorator ở trên, lệnh này bây giờ có thể xếp hàng gửi an toàn, không sợ bị ghi đè luồng
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("🚨 Lỗi khi gửi tin nhắn WebSocket cho User {}: {}", userId, e.getMessage());
            }
        } else {
            log.warn("⚠️ Không tìm thấy kết nối WebSocket đang mở cho User: {}", userId);
        }
    }

    private String extractUserId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}