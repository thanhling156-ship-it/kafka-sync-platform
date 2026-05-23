package com.example.rec_service.service;

import com.example.event_library.events.InternalCommunicationForRecommendation;
import com.example.rec_service.entity.OrderConfirmation;
import com.example.rec_service.entity.Product;
import com.example.rec_service.entity.ProductProjection;
import com.example.rec_service.handler.NotificationHandler;
import com.example.rec_service.repository.OrderConfirmationRepository;
import com.example.rec_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {
    private final ProductRepository productRepository;
    private final OrderConfirmationRepository confirmationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AiAgentService agentService;
    private final EmbeddingModel embeddingModel;
    private final NotificationHandler handler;

    // Dùng trên confirmations
    public void receiveConfirmation(String userId, String productId){
        String requestId = UUID.randomUUID().toString();
        OrderConfirmation orderConfirmation = new OrderConfirmation();
        orderConfirmation.setUserId(userId);
        orderConfirmation.setRecommendationId(requestId);
        confirmationRepository.save(orderConfirmation);

        System.out.println("Đã lưu request với ID: " + requestId);

        InternalCommunicationForRecommendation event = InternalCommunicationForRecommendation.builder()
                .recommendationId(requestId)
                .productId(productId)
                .recommendationText("")
                .build();
        kafkaTemplate.send("requestRecommendation", requestId, event );
    }

    // Dùng trên products
    public void handleRequest(InternalCommunicationForRecommendation event){
        Product product = productRepository.findById(event.getProductId()).get();
        String requestId = event.getRecommendationId();
        System.out.println("Đang xử lý request với ID: " + requestId);
        List<ProductProjection> list = productRepository.findNearestByProductId(product.getProductId());
        String text = agentService.askAi(product.getProductId(), product.getCategory(), list);
        event.setRecommendationText(text);
        kafkaTemplate.send("completeRecommendation", requestId, event );
    }

    // Dùng trên confirmations
    public void sendRecommendation(InternalCommunicationForRecommendation event){
        String reqId = event.getRecommendationId(); // Hoặc biến chứa ID của bạn

        // 1. Kiểm tra độ dài chuỗi để xem có ký tự ẩn không
        log.info("🔍 [Check ID] ID nhận được: '{}' (Độ dài: {})", reqId, (reqId != null ? reqId.length() : 0));

        // 2. Trim thử chuỗi để tìm kiếm
        String trimmedId = reqId != null ? reqId.trim() : "";

        OrderConfirmation orderConfirmation = confirmationRepository.findById(trimmedId)
                .orElseThrow(() -> new RuntimeException("🚨 [DB Error] Không tìm thấy ID dù Postgres báo có: '" + trimmedId + "'"));
        String userId = orderConfirmation.getUserId();
        String text = event.getRecommendationText();
        handler.pushNotification(userId, text);
    }

    public String saveProducts(ProductProjection productProjection){
        Product product = new Product();
        product.setProductId(productProjection.getProductId());
        product.setCategory(productProjection.getCategory());
        float[] vector = embeddingModel.embed(productProjection.getProductId());
        product.setEmbedding(vector);
        productRepository.save(product);
        return "Tạo sản phẩm "+productProjection.getProductId()+" thành công !";
    }
}
