package com.example.order_service.controller;

import com.example.order_service.dto.OrderRequest;
import com.example.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<String> placeOrder(@Valid @RequestBody OrderRequest request) {
        // Gọi Service để lưu DB và bắn Event
        String orderId = orderService.createOrder(request);

        // Trả về ngay lập tức để User không phải đợi lâu
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Đơn hàng " + orderId + " đã được tiếp nhận và đang xử lý.");
    }
}
