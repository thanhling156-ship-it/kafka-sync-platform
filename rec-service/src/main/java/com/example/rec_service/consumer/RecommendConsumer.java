package com.example.rec_service.consumer;

import com.example.event_library.events.InternalCommunicationForRecommendation;
import com.example.event_library.events.ShipCreatedEvent;
import com.example.rec_service.service.PredictionService;
import com.example.rec_service.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.event_library.topics.EventTopics.SHIP_SUCCESS;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendConsumer {
    private final RecommendService recommendService;
    private final PredictionService predictionService;
    @KafkaListener(topics = SHIP_SUCCESS, groupId = "rec-group")
    public void requestRecommendationForOrder(ShipCreatedEvent event) {
        if(event.getProductCode() == null){
            log.warn("product code is null");
        }
        log.info("📥 [Trace] Đã nhận sự kiện giao hàng thành công, chuẩn bị xử lý gợi ý cho sản phẩm: {}", event.getProductCode());
        recommendService.receiveConfirmation(event.getUserId(), event.getProductCode());
        predictionService.notificationAnomaly(event);
    }

    @KafkaListener(topics = "requestRecommendation", groupId = "rec-group")
    public void handleRequest (InternalCommunicationForRecommendation event){
        log.info("🔍 [Trace] Bắt đầu quy trình gợi ý AI cho sản phẩm: {}", event.getProductId());
        recommendService.handleRequest(event);
    }

    @KafkaListener(topics = "completeRecommendation", groupId = "rec-group")
    public void receive(InternalCommunicationForRecommendation  event) {
        log.info("✨ [Evaluation] Hoàn tất gợi ý AI: Tìm thấy tương đồng cho sản phẩm: {}", event.getProductId());
        recommendService.sendRecommendation(event);
    }
}
