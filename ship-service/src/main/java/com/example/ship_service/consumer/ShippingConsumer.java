package com.example.ship_service.consumer;

import com.example.event_library.events.*;
import com.example.ship_service.manager.ShippingManager;
import com.example.ship_service.repository.ShippingRepository;
import com.example.ship_service.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.function.Consumer; // IMPORT QUAN TRỌNG Ở ĐÂY
@Component
@Slf4j
@RequiredArgsConstructor
public class ShippingConsumer {

    private final ShippingManager shippingManager;

    /*
    Đối với StatusEvent thì status = PENDING/
     */

    // 1. Nghe báo cáo từ Pay Service
    @KafkaListener(topics = "pay-success", groupId = "ship-group")
    public void onPaySuccess(PayStatusEvent event) {
        shippingManager.updateAndCheck(event.getOrderId(), sn -> {
            sn.setPayStatus("SUCCESS");
            sn.setUserId(event.getUserId());
            sn.setAmount(event.getTotalPrice());
        });
    }

    @KafkaListener(topics = "pay-fail", groupId = "ship-group")
    public void onPayFail(PayStatusEvent event) {
        shippingManager.updateAndCheck(event.getOrderId(), sn -> {
            sn.setPayStatus("FAILED");
            sn.setUserId(event.getUserId());
            sn.setAmount(event.getTotalPrice());
            sn.setFlagFail(true);
        });
    }

    // 2. Nghe báo cáo từ Repo Service
    @KafkaListener(topics = "repo-success", groupId = "ship-group")
    public void onRepoSuccess(RepoStatusEvent event) {
        shippingManager.updateAndCheck(event.getOrderId(), sn -> {
            sn.setRepoStatus("SUCCESS");
            sn.setProductId(event.getProductId());
            sn.setQuantity(event.getQuantity());
            // Chỉ cập nhật bấy nhiêu, các trường như userId, amount, payStatus không bị động đến
        });
    }

    @KafkaListener(topics = "repo-fail", groupId = "ship-group")
    public void onRepoFail(RepoStatusEvent event) {
        // Gọi xuyên Class giúp kích hoạt @Transactional thành công
        shippingManager.updateAndCheck(event.getOrderId(), sn -> {
            sn.setRepoStatus("FAILED");
            sn.setProductId(event.getProductId());
            sn.setQuantity(event.getQuantity());
            sn.setFlagFail(true);
        });
    }
}
