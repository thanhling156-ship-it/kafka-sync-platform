# kafka-sync-platform

Hệ thống xử lý đơn hàng phân tán theo mô hình **Saga Choreography** sử dụng Apache Kafka làm message broker.

---

## Kiến trúc tổng quan

```
Client
  │
  ▼
[Order-Service] ──► Kafka: order-created ──┬──► [Pay-Service]  ──► pay-success-topic / pay-fail-topic ──┐
                                           │                                                             │
                                           └──► [Repo-Service] ──► repo-success-topic / repo-fail-topic ┤
                                                                                                         │
                          (Pay & Repo consume "order-created" independently and in parallel)             │
                                                                                                         ▼
                                                                                                  [Ship-Service] ── Arbitrator
                                                                                                         │
                                                                                          ┌──────────────┴──────────────┐
                                                                                     ship-success               ship-fail
                                                                                          │                           │
                                                                               Pay & Repo commit           Pay & Repo rollback
                                                                                          │                           │
                                                                                          └──────────┬────────────────┘
                                                                                                     ▼
                                                                                           [Order-Service] ── cập nhật trạng thái
```

---

## Tóm tắt 4 Service chính

---

### 1. Order-Service (Port 8083 | DB: `order_db`)

**Chức năng:** Tiếp nhận đơn hàng từ client, lưu trạng thái, và lắng nghe kết quả xử lý từ các service khác.

#### Cấu trúc package
```
controller/   → REST API
service/      → Business logic
repository/   → Data access (JPA)
entity/       → Order (JPA entity)
dto/          → OrderRequest
producer/     → OrderProducer (publish Kafka)
consumer/     → OrderConsumer (consume Kafka)
event/        → BaseEvent, PaymentFailedEvent, StockFailedEvent, ShippingCreatedEvent, ShippingFailedEvent
config/       → KafkaConfig
```

#### REST API
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/v1/orders` | Tạo đơn hàng mới, trả về `202 ACCEPTED` |

#### Entity: `Order`
| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | UUID | Primary key |
| `userId` | String | ID người dùng |
| `productId` | String | Mã sản phẩm |
| `quantity` | Integer | Số lượng |
| `totalPrice` | BigDecimal | Tổng giá trị |
| `status` | String | PENDING / CONFIRMED / SHIPPING / SHIPPED / CANCELLED_* |
| `address` | String | Địa chỉ giao hàng |
| `number` | String | Số điện thoại |
| `createdAt` | LocalDateTime | Thời điểm tạo |
| `version` | Long | Optimistic lock |

#### Luồng xử lý
1. `POST /api/v1/orders` → `OrderService.createOrder()` → lưu DB với status `PENDING`
2. `OrderProducer` publish `OrderCreatedEvent` lên topic **`order-created`** (key = orderId)
3. `OrderConsumer` lắng nghe các topic kết quả và gọi `updateOrderStatus()`:
   - `stock-failed` → `CANCELLED_OUT_OF_STOCK`
   - `payment-failed` → `CANCELLED_PAYMENT_FAILED`
   - `shipping-failed` → `CANCELLED_SHIPPING_FAILED`
   - `shipping-created` → `SHIPPING`

#### Kafka Topics
| Topic | Chiều | Mục đích |
|-------|-------|---------|
| `order-created` | OUT | Phát đơn hàng mới cho các service khác |
| `stock-failed` | IN | Repo báo hết hàng |
| `payment-failed` | IN | Pay báo không đủ tiền |
| `shipping-failed` | IN | Ship báo không thể giao |
| `shipping-created` | IN | Ship báo đang vận chuyển |

---

### 2. Pay-Service (Port 8085 | DB: `pay_db`)

**Chức năng:** Kiểm tra số dư tài khoản, tạm giữ tiền (reserve), cam kết hoặc hoàn trả dựa trên kết quả từ Ship-Service.

#### Cấu trúc package
```
service/      → PayService (Two-phase payment)
consumer/     → PayConsumer (consume Kafka)
entity/       → User, PaymentReserve
repository/   → UserRepository, PaymentReserveRepository
config/       → KafkaConfig
```

#### Entity: `User`
| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | Long | Technical PK (auto-increment, dùng nội bộ DB) |
| `userId` | String | Business key – Unique; được dùng để tra cứu theo ID nghiệp vụ |
| `balance` | BigDecimal | Số dư hiện tại |

> **Pessimistic Write Lock** được dùng khi truy vấn `findByUserId()` để tránh race condition.

#### Entity: `PaymentReserve`
| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | Long | PK |
| `orderId` | String | Unique |
| `userId` | String | ID người dùng |
| `amount` | BigDecimal | Số tiền tạm giữ |
| `status` | String | PENDING / COMPLETED / CANCELLED |

#### Luồng xử lý (Two-Phase Payment)
1. **Phase 1 – Reserve:** `handleOrderCreated()` nhận `order-created`
   - Kiểm tra số dư (`hasEnoughBalance()`)
   - **Đủ tiền:** Tạo `PaymentReserve` PENDING → publish **`pay-success-topic`**
   - **Không đủ:** publish **`pay-fail-topic`**
2. **Phase 2 – Finalize:** `finalizeOrder()` nhận kết quả từ Ship
   - `ship-success` → Trừ tiền thực từ `User.balance`, xóa reserve record
   - `ship-fail` → Chỉ xóa reserve record (không trừ tiền)

#### Kafka Topics
| Topic | Chiều | Mục đích |
|-------|-------|---------|
| `order-created` | IN | Nhận đơn hàng mới |
| `pay-success-topic` | OUT | Xác nhận tạm giữ tiền thành công |
| `pay-fail-topic` | OUT | Báo không đủ số dư |
| `ship-success` | IN | Ship thành công → cam kết trừ tiền |
| `ship-fail` | IN | Ship thất bại → hoàn trả (không trừ) |

---

### 3. Repo-Service (Port 8084 | DB: `repo_db`)

**Chức năng:** Kiểm tra tồn kho, tạm giữ hàng (reserve), cam kết hoặc hoàn trả dựa trên kết quả từ Ship-Service.

#### Cấu trúc package
```
service/      → RepoService (Two-phase inventory)
consumer/     → RepoConsumer (consume Kafka)
entity/       → Product, StockReserve
repository/   → ProductRepository, StockReserveRepository
config/       → KafkaConfig
```

#### Entity: `Product`
| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | Long | Technical PK (auto-increment, dùng nội bộ DB) |
| `productCode` | String | Business key – Unique; được dùng để tra cứu theo mã sản phẩm nghiệp vụ |
| `name` | String | Tên sản phẩm |
| `stockQuantity` | Integer | Số lượng tồn kho |
| `price` | BigDecimal | Đơn giá |

> **Pessimistic Write Lock** được dùng khi truy vấn `findByProductCode()`.

#### Entity: `StockReserve`
| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | Long | PK |
| `orderId` | String | Unique |
| `productCode` | String | Mã sản phẩm |
| `quantity` | Integer | Số lượng tạm giữ |
| `status` | String | PENDING / COMPLETED / CANCELLED |

#### Luồng xử lý (Two-Phase Inventory)
1. **Phase 1 – Reserve:** `handleOrderCreated()` nhận `order-created`
   - Kiểm tra tồn kho (`hasEnoughStock()`)
   - **Còn hàng:** Tạo `StockReserve` PENDING → publish **`repo-success-topic`**
   - **Hết hàng:** publish **`repo-fail-topic`**
2. **Phase 2 – Finalize:** `finalizeOrder()` nhận kết quả từ Ship
   - `ship-success` → Giảm `Product.stockQuantity`, xóa reserve record
   - `ship-fail` → Chỉ xóa reserve record (không thay đổi tồn kho)

#### Kafka Topics
| Topic | Chiều | Mục đích |
|-------|-------|---------|
| `order-created` | IN | Nhận đơn hàng mới |
| `repo-success-topic` | OUT | Xác nhận tạm giữ hàng thành công |
| `repo-fail-topic` | OUT | Báo hết hàng |
| `ship-success` | IN | Ship thành công → cam kết trừ tồn kho |
| `ship-fail` | IN | Ship thất bại → hoàn trả (không trừ) |

---

### 4. Ship-Service (Port 8086 | DB: `ship_db`)

**Chức năng:** Đóng vai trò **arbitrator (trọng tài)** — tổng hợp kết quả từ Pay & Repo, đưa ra quyết định cuối cùng cho toàn bộ saga.

#### Cấu trúc package
```
service/      → ShippingService (Arbitrator)
consumer/     → ShippingConsumer (consume tất cả status events)
entity/       → ShippingSnapshot
repository/   → ShippingRepository
config/       → KafkaConfig
```

#### Entity: `ShippingSnapshot`
| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `orderId` | String | PK |
| `payStatus` | String | PENDING / SUCCESS / FAILED |
| `repoStatus` | String | PENDING / SUCCESS / FAILED |
| `userId` | String | Backup để rollback |
| `amount` | BigDecimal | Backup để rollback |
| `productId` | String | Backup để rollback |
| `quantity` | Integer | Backup để rollback |
| `version` | Long | Optimistic lock |

#### Luồng xử lý (Arbitration)
1. `onOrderCreated()` (topic: `order-created`) → Tạo `ShippingSnapshot` với cả 2 status = `PENDING`
2. Khi nhận pay/repo status event:
   - `onPaySuccess()` / `onPayFail()` → Cập nhật `payStatus`
   - `onRepoSuccess()` / `onRepoFail()` → Cập nhật `repoStatus`
   - Gọi `checkAndFinalize()`
3. `checkAndFinalize()`:
   - Nếu một trong hai còn `PENDING` → **chờ**
   - Nếu **cả hai SUCCESS** → publish **`ship-success`** → Pay & Repo cam kết
   - Nếu **có bất kỳ FAILED** → publish **`ship-fail`** → Pay & Repo hoàn trả

#### Kafka Topics
| Topic | Chiều | Mục đích |
|-------|-------|---------|
| `order-created` | IN | Khởi tạo snapshot theo dõi |
| `pay-success-topic` | IN | Pay báo reserve thành công |
| `pay-fail-topic` | IN | Pay báo không đủ số dư |
| `repo-success-topic` | IN | Repo báo reserve thành công |
| `repo-fail-topic` | IN | Repo báo hết hàng |
| `ship-success` | OUT | Kết quả: PASS → các service cam kết |
| `ship-fail` | OUT | Kết quả: FAIL → các service hoàn trả |

---

## Luồng xử lý đầy đủ

```
1. Client  ──POST /api/v1/orders──►  Order-Service
           Lưu Order PENDING, publish "order-created"

2. Pay-Service nhận "order-created"
   → Kiểm tra số dư
   → Tạm giữ tiền (PaymentReserve) hoặc báo lỗi
   → Publish "pay-success-topic" hoặc "pay-fail-topic"

3. Repo-Service nhận "order-created" (song song với Pay)
   → Kiểm tra tồn kho
   → Tạm giữ hàng (StockReserve) hoặc báo lỗi
   → Publish "repo-success-topic" hoặc "repo-fail-topic"

4. Ship-Service nhận "order-created" → Tạo ShippingSnapshot (payStatus=PENDING, repoStatus=PENDING)
   Ship-Service nhận kết quả Pay & Repo → Cập nhật snapshot
   Khi cả hai có kết quả:
     ✅ Cả hai SUCCESS → Publish "ship-success"
     ❌ Có FAILED      → Publish "ship-fail"

5a. (ship-success)
    Pay-Service: Trừ tiền thực từ tài khoản
    Repo-Service: Giảm tồn kho thực tế
    Order-Service: Cập nhật status → SHIPPING

5b. (ship-fail)
    Pay-Service: Xóa PaymentReserve (không trừ tiền)
    Repo-Service: Xóa StockReserve (không trừ hàng)
    Order-Service: Cập nhật status → CANCELLED_*
```

---

## Các pattern đảm bảo tính nhất quán

| Pattern | Áp dụng | Mục đích |
|---------|---------|---------|
| **Saga Choreography** | Toàn hệ thống | Phân tán transaction qua nhiều service |
| **Two-Phase Reserve** | Pay & Repo | Tạm giữ resource trước khi cam kết |
| **Pessimistic Lock** | User, Product queries | Tránh race condition khi cập nhật số dư/tồn kho |
| **Optimistic Lock** (`@Version`) | Order entity | Kiểm soát concurrency ở lớp order |
| **Idempotency** | Pay & Repo finalize | Xử lý duplicate message bằng existence-based logic |
| **Message Key Partitioning** | OrderId làm Kafka key | Đảm bảo event cùng order đi vào cùng partition |
| **Fat Event** | `OrderCreatedEvent` | Chứa đủ dữ liệu để downstream không cần gọi API ngược |

---

## Công nghệ sử dụng

| Thành phần | Công nghệ |
|-----------|-----------|
| Framework | Spring Boot |
| ORM | JPA / Hibernate |
| Database | PostgreSQL |
| Message Broker | Apache Kafka |
| Serialization | Jackson JSON |
| Service Discovery | Eureka |
| Build Tool | Maven |
