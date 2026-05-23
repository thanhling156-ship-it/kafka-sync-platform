package com.example.rec_service.service;

import com.example.rec_service.entity.ProductProjection;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAgentService {

    private final ChatClient chatClient;

    // Spring AI sẽ tự động inject ChatClient.Builder liên kết với Gemini
    // dựa trên starter bạn đã cấu hình trong pom.xml
    public AiAgentService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // Trả về thông báo
    public String askAi(String productName, String category, List<ProductProjection> projectionList) {
        return chatClient.prompt()
                .system("""
        Bạn là chuyên gia Marketing tư vấn bán hàng.
        Nhiệm vụ: Viết một thông báo gợi ý sản phẩm đồng bộ ngắn gọn (khoảng 3 câu).
        
        Yêu cầu:
        - Câu 1: Chúc mừng khách hàng mua %s và dẫn dắt sang sản phẩm gợi ý.
        - Câu 2: Liệt kê nhanh các sản phẩm gợi ý kèm theo lợi ích ngắn gọn nhất (ví dụ: giúp nâng cao hiệu suất, đồng bộ hoàn hảo).
        - Câu 3: Kết luận
        - Văn phong thân thiện, thu hút nhưng phải súc tích.
        - Không markdown, không giải thích, chỉ trả về chuỗi thông báo duy nhất.
        """)
                .user(String.format("""
        Sản phẩm đã mua: %s (Danh mục: %s)
        Sản phẩm gợi ý: %s
        """, productName, category, projectionList))
                .call()
                .content();
    }
}