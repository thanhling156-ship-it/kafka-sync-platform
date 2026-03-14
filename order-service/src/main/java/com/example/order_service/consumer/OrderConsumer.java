package com.example.order_service.consumer;

import com.example.event_library.*;
import com.example.order_service.event.PaymentFailedEvent;
import com.example.order_service.event.ShippingCreatedEvent;
import com.example.order_service.event.ShippingFailedEvent;
import com.example.order_service.event.StockFailedEvent;
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
     * 1. NHẬN LỆNH HỦY ĐƠN DO HẾT HÀNG (Từ Inventory Service)
     * Khi kho báo không đủ hàng để giữ (Reserve failed).
     */
    @KafkaListener(topics = "stock-failed", groupId = "order-group")
    public void handleStockFailed(StockFailedEvent event) {
        log.warn("❌ Hết hàng trong kho - OrderID: {}. Đang hủy đơn hàng...", event.getOrderId());
        // OrderService chỉ cập nhật trạng thái đơn hàng của chính nó
        orderService.updateOrderStatus(event.getOrderId(), "CANCELLED_OUT_OF_STOCK");
    }

    /**
     * 2. NHẬN LỆNH HỦY ĐƠN DO THANH TOÁN LỖI (Từ Payment Service)
     * Khách hết tiền hoặc lỗi giao dịch -> Hủy đơn hàng.
     */
    @KafkaListener(topics = "payment-failed", groupId = "order-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("❌ Thanh toán thất bại - OrderID: {}. Đang hủy đơn hàng...", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "CANCELLED_PAYMENT_FAILED");
    }

    /**
     * 3. NHẬN LỆNH HỦY ĐƠN DO GIAO HÀNG LỖI (Từ Shipping Service)
     * Không tìm thấy Shipper hoặc lỗi vận chuyển -> Hủy đơn hàng.
     */
    @KafkaListener(topics = "shipping-failed", groupId = "order-group")
    public void handleShippingFailed(ShippingFailedEvent event) {
        log.warn("🚚 Giao hàng thất bại - OrderID: {}. Đang hủy đơn hàng...", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "CANCELLED_SHIPPING_FAILED");
    }

    /**
     * 4. NHẬN LỆNH XÁC NHẬN ĐƠN HÀNG (Từ Shipping Service)
     * Khi Shipper đã tiếp nhận đơn hàng thành công -> Đơn hàng chuyển sang trạng thái đang giao.
     */
    @KafkaListener(topics = "shipping-created", groupId = "order-group")
    public void handleShippingCreated(ShippingCreatedEvent event) {
        log.info("✅ Đơn hàng đã được bàn giao cho đơn vị vận chuyển - OrderID: {}", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "SHIPPING");
    }
}
