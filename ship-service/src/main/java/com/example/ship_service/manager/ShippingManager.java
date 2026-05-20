package com.example.ship_service.manager;

import com.example.ship_service.entity.ShippingSnapshot;
import com.example.ship_service.repository.ShippingRepository;
import com.example.ship_service.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ShippingManager {

    @Autowired
    private ShippingRepository repository;

    @Autowired
    private ShippingService shippingService;

    @Transactional // <--- Bây giờ Transaction đã được kích hoạt an toàn 100%
    // Tác dụng: "Được ăn cả - Ngã về không"
    public void updateAndCheck(String orderId, Consumer<ShippingSnapshot> updater) {
        // Tìm hoặc tạo mới trên bộ nhớ RAM
        ShippingSnapshot sn = repository.findById(orderId)
                .orElseGet(() -> {
                    ShippingSnapshot newSn = new ShippingSnapshot();
                    newSn.setOrderId(orderId);
                    return newSn;
                });

        // Chạy đoạn Lamda để đắp dữ liệu cụ thể của từng Event vào
        updater.accept(sn);

        // Lưu xuống DB (Sinh lệnh INSERT hoặc UPDATE tương ứng)
        repository.upsertAndEnrich(sn);

        // Chạy nốt logic kiểm tra cuối cùng trong cùng 1 Transaction
        shippingService.checkAndFinalize(orderId);
    }
}
