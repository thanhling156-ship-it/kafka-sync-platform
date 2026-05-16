package com.example.ship_service.consumer;

import com.example.event_library.*;
import com.example.ship_service.entity.ShippingSnapshot;
import com.example.ship_service.repository.ShippingRepository;
import com.example.ship_service.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer; // IMPORT QUAN TRỌNG Ở ĐÂY

@Component
@Slf4j
@RequiredArgsConstructor
public class ShippingConsumer {

    private final ShippingRepository repository;
    private final ShippingService shippingService;

    /*
    Đối với StatusEvent thì status = PENDING/
     */

    // 1. Nghe báo cáo từ Pay Service
    @KafkaListener(topics = "pay-success-topic", groupId = "ship-group")
    public void onPaySuccess(PayStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> {
            sn.setPayStatus("SUCCESS");
            sn.setUserId(event.getUserId());
            sn.setAmount(event.getTotalPrice());
        });
    }

    @KafkaListener(topics = "pay-fail-topic", groupId = "ship-group")
    public void onPayFail(PayStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> {
            sn.setPayStatus("FAILED");
            sn.setUserId(event.getUserId());
            sn.setAmount(event.getTotalPrice());
        });
    }

    // 2. Nghe báo cáo từ Repo Service
    @KafkaListener(topics = "repo-success-topic", groupId = "ship-group")
    public void onRepoSuccess(RepoStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> {
            sn.setRepoStatus("SUCCESS");
            sn.setProductId(event.getProductId());
            sn.setQuantity(event.getQuantity());
            // Chỉ cập nhật bấy nhiêu, các trường như userId, amount, payStatus không bị động đến
        });
    }

    @KafkaListener(topics = "repo-fail-topic", groupId = "ship-group")
    public void onRepoFail(RepoStatusEvent event) {
        updateAndCheck(event.getOrderId(), sn -> {
            sn.setRepoStatus("FAILED");
            sn.setProductId(event.getProductId());
            sn.setQuantity(event.getQuantity());
        });
    }

    private void updateAndCheck(String orderId, Consumer<ShippingSnapshot> updater) {
        // Nếu chưa có trong DB, tạo mới một Entity CHỈ CÓ orderId
        // Các trường payStatus, repoStatus tự động là "PENDING" theo định nghĩa của Entity
        ShippingSnapshot sn = repository.findById(orderId)
                .orElseGet(() -> ShippingSnapshot.builder().orderId(orderId).build());
        // Chạy updater để đắp thêm dữ liệu từ Event vào
        updater.accept(sn);
        // Lưu vào DB (JPA tự biết là INSERT nếu mới, UPDATE nếu đã có)
        repository.save(sn);
        shippingService.checkAndFinalize(orderId);
    }
}
