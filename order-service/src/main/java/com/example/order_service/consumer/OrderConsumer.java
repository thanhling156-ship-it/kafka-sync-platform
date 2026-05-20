package com.example.order_service.consumer;

import com.example.event_library.events.PayStatusEvent;
import com.example.event_library.events.RepoStatusEvent;
import com.example.event_library.events.ShipCreatedEvent;
import com.example.event_library.events.ShipSuccessEvent;
import com.example.event_library.topics.EventTopics;
import com.example.order_service.constant.StatusCode;
import com.example.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
//Quản lý đơn hàng
public class OrderConsumer {
    private final OrderService orderService;

    /**
     * 1. NHẬN LỆNH HỦY ĐƠN DO HẾT HÀNG (Từ Repo Service)
     * Khi kho báo không đủ hàng để giữ (Reserve failed).
     */
    @KafkaListener(topics = EventTopics.REPO_FAIL, groupId = "order-group")
    public void handleStockFailed(RepoStatusEvent event) {
        log.warn("📦❌ Hết hàng trong kho - OrderID: {}. Đang hủy đơn hàng...", event.getOrderId());
        // OrderService chỉ cập nhật trạng thái đơn hàng của chính nó
        orderService.cancelOrderStatus(event.getOrderId(), StatusCode.REPO_FAIL);
    }

    /**
     * 2. NHẬN LỆNH HỦY ĐƠN DO THANH TOÁN LỖI (Từ Pay Service)
     * Khách hết tiền hoặc lỗi giao dịch -> Hủy đơn hàng.
     */
    @KafkaListener(topics = EventTopics.PAY_FAIL, groupId = "order-group")
    public void handlePaymentFailed(PayStatusEvent event) {
        log.warn("💳❌ Thanh toán thất bại - OrderID: {}. Đang hủy đơn hàng...", event.getOrderId());
        orderService.cancelOrderStatus(event.getOrderId(),  StatusCode.PAY_FAIL);
    }

    @KafkaListener(topics = EventTopics.SHIP_SUCCESS, groupId = "order-group")
    public void handlePaymentFailed(ShipCreatedEvent event) {
        log.info("🚚✅ Giao hàng thành công - OrderID: {}. Tiến hành cập nhật trạng thái đơn hàng...", event.getOrderId());
        orderService.completeOrderStatus(event.getOrderId(), StatusCode.SHIP_SUCCESS);
    }
}
