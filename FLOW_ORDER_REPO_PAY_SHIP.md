# Flow tổng quan: Order / Repo / Pay / Ship

Tài liệu này mô tả **flow nghiệp vụ** (happy-path) giữa 4 service chính trong repo:

- `order-service`
- `repo-service`
- `pay-service`
- `ship-service`

> Ghi chú: Repo này hiện là dạng multi-module/multi-service. Mỗi service thường có cấu trúc Spring Boot (`src/main/java`, `src/main/resources`, `pom.xml`, `Dockerfile`).

---

## 1) Order Service (`order-service`)

### Vai trò
- Nhận request tạo/điều chỉnh đơn hàng.
- Publish event (Kafka) để các service khác (Repo/Pay/Ship) xử lý bất đồng bộ.

### Flow (mức khái niệm)
1. Client gọi API (qua `api-gateway`) vào `order-service`.
2. `order-service` validate input, tạo Order, lưu DB.
3. `order-service` phát sự kiện (ví dụ: `OrderCreated` / `OrderConfirmed`) lên Kafka.
4. Chờ các service downstream xử lý (Repo/Pay/Ship). Trạng thái Order được cập nhật dần theo các event phản hồi.

### Điểm cần nhìn trong code
- REST Controller: `order-service/src/main/java/**/controller/**`
- Service layer: `order-service/src/main/java/**/service/**`
- Kafka producer/consumer config: `order-service/src/main/java/**/kafka/**` hoặc `**/config/**`
- DTO/Entity/Repository: `order-service/src/main/java/**/dto/**`, `**/entity/**`, `**/repository/**`

---

## 2) Repo Service (`repo-service`)

### Vai trò
- Quản lý “kho” / “inventory reservation” (tên service là repo-service nhưng thường đóng vai trò reserve/giữ hàng).
- Consume event từ Order để reserve/release stock.
- Publish event kết quả về Kafka để Order/Pay/Ship biết trạng thái.

### Flow (mức khái niệm)
1. `repo-service` consume event từ Kafka (ví dụ: `OrderCreated`).
2. Kiểm tra tồn kho / điều kiện reserve.
3. Nếu OK: reserve stock và publish event `StockReserved`.
4. Nếu fail: publish event `StockReserveFailed` (để `order-service` rollback/cancel hoặc đổi trạng thái).

### Điểm cần nhìn trong code
- Kafka consumer handlers: `repo-service/src/main/java/**/kafka/**`
- Logic reserve/release: `repo-service/src/main/java/**/service/**`
- Storage layer: `repo-service/src/main/java/**/entity/**`, `**/repository/**`

---

## 3) Pay Service (`pay-service`)

### Vai trò
- Xử lý thanh toán (authorize/capture/refund tuỳ thiết kế).
- Consume các event (thường sau khi reserve stock thành công) để bắt đầu payment.
- Publish event kết quả: `PaymentSucceeded` / `PaymentFailed`.

### Flow (mức khái niệm)
1. `pay-service` consume event phù hợp (ví dụ: `StockReserved` hoặc `OrderConfirmed`).
2. Tạo payment transaction.
3. Gọi cổng thanh toán (nếu có) hoặc mô phỏng payment.
4. Publish `PaymentSucceeded` hoặc `PaymentFailed`.

### Điểm cần nhìn trong code
- Kafka consumer: `pay-service/src/main/java/**/kafka/**`
- Payment orchestration: `pay-service/src/main/java/**/service/**`
- Integration (nếu có): `pay-service/src/main/java/**/client/**` hoặc `**/integration/**`

---

## 4) Ship Service (`ship-service`)

### Vai trò
- Tạo shipment / giao vận khi đơn hàng đủ điều kiện (thường sau payment thành công).
- Consume payment/order events để tạo vận đơn.
- Publish event: `ShipmentCreated` / `ShipmentFailed` / `ShipmentDelivered` (tuỳ).

### Flow (mức khái niệm)
1. `ship-service` consume event (ví dụ: `PaymentSucceeded`).
2. Tạo shipment, gán carrier, lưu DB.
3. Publish event `ShipmentCreated`.
4. (Tuỳ) update trạng thái giao hàng qua các event tiếp theo.

### Điểm cần nhìn trong code
- Kafka consumer: `ship-service/src/main/java/**/kafka/**`
- Shipment domain/service: `ship-service/src/main/java/**/service/**`
- Lưu trữ: `ship-service/src/main/java/**/entity/**`, `**/repository/**`

---

## 5) End-to-end (Happy path) đề xuất

```text
Client
  -> api-gateway
    -> order-service (create order)
      -> Kafka: OrderCreated
        -> repo-service (reserve stock)
          -> Kafka: StockReserved
            -> pay-service (process payment)
              -> Kafka: PaymentSucceeded
                -> ship-service (create shipment)
                  -> Kafka: ShipmentCreated
                    -> order-service (update order status: COMPLETED / READY_TO_SHIP ...)
```

---

## 6) Bạn muốn mô tả flow theo code “thực tế” (đúng topic/event/class) không?

Mình có thể cập nhật tài liệu này thành **đúng theo code hiện tại** (topic Kafka, tên event, class handler, package) nếu bạn cho mình 1 trong các lựa chọn:

1) Bạn muốn mình **tự dò code** 4 service để trích đúng tên event/topic/class, hay
2) Bạn gửi giúp mình:
   - tên topic Kafka đang dùng cho Order/Repo/Pay/Ship, hoặc
   - 1 file config (ví dụ `application.yml`) của mỗi service.
