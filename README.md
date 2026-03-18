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

---

## Tại sao AI không đọc được link GitHub của bạn?

> **Hầu hết các AI chat (ChatGPT, Claude.ai, Gemini, v.v.) KHÔNG có khả năng truy cập internet theo thời gian thực.**  
> Dù repo của bạn đã để Public, các AI này vẫn không thể mở URL để đọc code.

### Cách chia sẻ code với AI đúng cách

| Cách | Mô tả |
|------|-------|
| ✅ **Copy-paste nội dung README này** | Chọn tất cả nội dung file README.md và dán vào chat AI |
| ✅ **Copy từng file source code** | Mở file `.java` trên GitHub → nút **Raw** → copy toàn bộ nội dung → dán vào AI |
| ✅ **Dùng AI có web browsing** | Một số AI như ChatGPT (với plugin Browse) hoặc Perplexity có thể mở URL |
| ✅ **Dùng GitHub Copilot** | Copilot tích hợp trực tiếp vào IDE, đọc code trong workspace của bạn |
| ✅ **README này đã embed sẵn source code** | Phần bên dưới chứa toàn bộ source code – bạn chỉ cần copy README này |

---

## Source Code Đầy Đủ (dùng để chia sẻ với AI)

> Phần này embed toàn bộ source code của 4 service chính và event-library.  
> Chỉ cần copy toàn bộ README này và dán vào bất kỳ AI nào là đủ context.

---

### event-library — Các Event dùng chung

#### `OrderCreatedEvent.java`
```java
package com.example.event_library;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private String productId; // ID hoặc mã sản phẩm để trừ kho
    private Integer quantity;  // Số lượng khách đặt
    private Double totalPrice;
    private String address;    // Thông tin này Repo có thể không dùng nhưng vẫn có trong Fat Event
    private String phone;
}
```

#### `PayStatusEvent.java`
```java
package com.example.event_library;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PayStatusEvent {
    private String orderId;
    private String status;  // SUCCESS hoặc FAILED
    private String message; // Lý do
}
```

#### `RepoStatusEvent.java`
```java
package com.example.event_library;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepoStatusEvent {
    private String orderId;
    private String status;  // SUCCESS hoặc FAILED
    private String message; // Lý do
}
```

#### `ShipCreatedEvent.java`
```java
package com.example.event_library;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
public class ShipCreatedEvent {
    private String orderId;
    private String status;       // "SUCCESS" hoặc "FAILED"
    private String productCode;
    private Integer quantity;
    private Double totalAmount;
    private String message;
    private LocalDateTime createdAt;
}
```

---

### order-service

#### `Order.java` (Entity)
```java
package com.example.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    private String id; // UUID truyền từ Service

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double totalPrice; // tính từ orderRequest

    @Column(nullable = false)
    private String status; // PENDING, CONFIRMED, CANCELLED_..., SHIPPING

    @Version
    private Long version; // Optimistic Locking

    private String address;
    private String number;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public boolean isFinalState() {
        return this.status.startsWith("CANCELLED") || this.status.equals("SHIPPED");
    }
}
```

#### `OrderRequest.java` (DTO)
```java
package com.example.order_service.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {
    @NotBlank private String userId;
    @NotBlank private String productId;
    @Min(1)   private Integer quantity;
    @Positive private Double unitPrice;
    private String address;
    private String number;
}
```

#### `OrderController.java`
```java
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
        String orderId = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Đơn hàng " + orderId + " đã được tiếp nhận và đang xử lý.");
    }
}
```

#### `OrderService.java`
```java
package com.example.order_service.service;

import com.example.order_service.dto.OrderRequest;
import com.example.order_service.entity.Order;
import com.example.order_service.producer.OrderProducer;
import com.example.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    @Transactional
    public String createOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalPrice(request.getUnitPrice() * request.getQuantity())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .address(request.getAddress())
                .number(request.getNumber())
                .build();

        orderRepository.save(order);
        log.info("💾 Đã lưu Order {} vào Database với trạng thái PENDING", orderId);

        orderProducer.publishOrderCreated(order);
        log.info("🚀 Đã bắn Fat Event cho Order {}", orderId);

        return orderId;
    }

    @Transactional
    public void updateOrderStatus(String orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Idempotency: chỉ cập nhật nếu chưa bị huỷ
        if (!order.getStatus().startsWith("CANCELLED")) {
            order.setStatus(status);
            orderRepository.save(order);
            log.info("Cập nhật trạng thái Order {} sang {}", orderId, status);
        }
    }
}
```

#### `OrderProducer.java`
```java
package com.example.order_service.producer;

import com.example.order_service.entity.Order;
import com.example.event_library.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice())
                .address(order.getAddress())
                .build();

        // Dùng orderId làm Message Key → đảm bảo cùng 1 Partition
        kafkaTemplate.send("order-created", order.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ Fat Event lên Kafka! OrderId: {} | Partition: {}",
                                order.getId(), result.getRecordMetadata().partition());
                    } else {
                        log.error("❌ Bắn tin thất bại cho OrderId: {}. Lỗi: {}",
                                order.getId(), ex.getMessage());
                    }
                });
    }
}
```

#### `OrderConsumer.java`
```java
package com.example.order_service.consumer;

import com.example.order_service.event.*;
import com.example.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderConsumer {
    private final OrderService orderService;

    @KafkaListener(topics = "stock-failed", groupId = "order-group")
    public void handleStockFailed(StockFailedEvent event) {
        log.warn("❌ Hết hàng - OrderID: {}", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "CANCELLED_OUT_OF_STOCK");
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("❌ Thanh toán thất bại - OrderID: {}", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "CANCELLED_PAYMENT_FAILED");
    }

    @KafkaListener(topics = "shipping-failed", groupId = "order-group")
    public void handleShippingFailed(ShippingFailedEvent event) {
        log.warn("🚚 Giao hàng thất bại - OrderID: {}", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "CANCELLED_SHIPPING_FAILED");
    }

    @KafkaListener(topics = "shipping-created", groupId = "order-group")
    public void handleShippingCreated(ShippingCreatedEvent event) {
        log.info("✅ Đơn hàng bàn giao vận chuyển - OrderID: {}", event.getOrderId());
        orderService.updateOrderStatus(event.getOrderId(), "SHIPPING");
    }
}
```

---

### pay-service

#### `User.java` (Entity)
```java
package com.example.pay_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                         // Technical PK (nội bộ DB)

    @Column(name = "user_id", unique = true)
    private String userId;                   // Business key – dùng để tra cứu

    private Double balance;

    public boolean hasEnoughBalance(Double amount) { return this.balance >= amount; }
    public void deduct(Double amount)               { this.balance -= amount; }
    public void refund(Double amount)               { this.balance += amount; }
}
```

#### `PaymentReserve.java` (Entity)
```java
package com.example.pay_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_reserves")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentReserve {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    private String status; // PENDING, COMPLETED, CANCELLED
}
```

#### `PayService.java`
```java
package com.example.pay_service.service;

import com.example.event_library.OrderCreatedEvent;
import com.example.event_library.PayStatusEvent;
import com.example.pay_service.entity.PaymentReserve;
import com.example.pay_service.entity.User;
import com.example.pay_service.repository.PaymentReserveRepository;
import com.example.pay_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentReserveRepository paymentReserveRepository;

    /** Phase 1: Kiểm tra số dư và RESERVE (phong toả tiền) */
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            User user = userRepository.findByUserId(event.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + event.getUserId()));

            if (user.hasEnoughBalance(event.getTotalPrice())) {
                PaymentReserve reserve = PaymentReserve.builder()
                        .orderId(event.getOrderId())
                        .userId(event.getUserId())
                        .amount(event.getTotalPrice())
                        .status("PENDING")
                        .build();
                paymentReserveRepository.save(reserve);
                log.info("✅ Reserve tiền thành công cho đơn: {}", event.getOrderId());
                sendStatus("pay-success-topic", event.getOrderId(), "SUCCESS", "Payment reserved");
            } else {
                log.warn("❌ Không đủ số dư cho đơn: {}", event.getOrderId());
                sendStatus("pay-fail-topic", event.getOrderId(), "FAILED", "Insufficient balance");
            }
        } catch (Exception e) {
            log.error("💥 Lỗi Reserve tiền cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("pay-fail-topic", event.getOrderId(), "FAILED", "System error: " + e.getMessage());
        }
    }

    /** Phase 2: COMMIT hoặc ROLLBACK dựa trên phán quyết Ship */
    @Transactional
    public void finalizeOrder(String orderId, String status) {
        Optional<PaymentReserve> reserveOpt = paymentReserveRepository.findByOrderId(orderId);
        if (reserveOpt.isEmpty()) {
            log.info("ℹ️ [IDEMPOTENT] Không có bản ghi phong toả cho đơn: {}. Bỏ qua.", orderId);
            return;
        }
        PaymentReserve reserve = reserveOpt.get();

        if ("SUCCESS".equalsIgnoreCase(status)) {
            User user = userRepository.findByUserId(reserve.getUserId())
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
            user.setBalance(user.getBalance() - reserve.getAmount());
            userRepository.save(user);
            log.info("💰 [COMMIT] Trừ tiền thật cho User: {} - Số tiền: {}", reserve.getUserId(), reserve.getAmount());
        } else {
            log.warn("🔄 [ROLLBACK] Giải toả phong toả tiền cho đơn: {}", orderId);
            // Tiền vẫn trong tài khoản; chỉ cần xoá bản ghi phong toả
        }
        paymentReserveRepository.delete(reserve);
    }

    @Transactional
    public void refundPayment(String orderId, Double amount, String userId) {
        userRepository.findByUserId(userId).ifPresent(user -> {
            user.refund(amount);
            userRepository.save(user);
        });
    }

    private void sendStatus(String topic, String orderId, String status, String message) {
        kafkaTemplate.send(topic, PayStatusEvent.builder()
                .orderId(orderId).status(status).message(message).build());
    }
}
```

#### `PayConsumer.java`
```java
package com.example.pay_service.consumer;

import com.example.event_library.*;
import com.example.pay_service.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PayConsumer {

    private final PayService payService;

    @KafkaListener(topics = "order-created", groupId = "pay-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("💰 [ORDER-CREATED] Nhận đơn: {}", event.getOrderId());
        payService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "ship-fail", groupId = "pay-group")
    public void handleShipFail(ShipCreatedEvent event) {
        log.warn("🔄 [PAY-ROLLBACK] Nhận lệnh giải toả tiền đơn: {}", event.getOrderId());
        payService.finalizeOrder(event.getOrderId(), "FAIL");
    }

    @KafkaListener(topics = "ship-success", groupId = "pay-group")
    public void handleShipSuccess(ShipCreatedEvent event) {
        log.info("✅ [PAY-COMMIT] Nhận lệnh trừ tiền thật đơn: {}", event.getOrderId());
        payService.finalizeOrder(event.getOrderId(), "SUCCESS");
    }
}
```

---

### repo-service

#### `Product.java` (Entity)
```java
package com.example.repo_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                              // Technical PK (nội bộ DB)

    @Column(name = "product_code", nullable = false, unique = true)
    private String productCode;                   // Business key – khớp productId từ event

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "price")
    private Double price;

    public boolean hasEnoughStock(int qty) { return this.stockQuantity >= qty; }
    public void reduceStock(int qty)       { this.stockQuantity -= qty; }
}
```

#### `StockReserve.java` (Entity)
```java
package com.example.repo_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_reserves")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockReserve {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "status")
    private String status; // PENDING, COMPLETED, CANCELLED
}
```

#### `RepoService.java`
```java
package com.example.repo_service.service;

import com.example.event_library.OrderCreatedEvent;
import com.example.event_library.RepoStatusEvent;
import com.example.repo_service.entity.Product;
import com.example.repo_service.entity.StockReserve;
import com.example.repo_service.repository.ProductRepository;
import com.example.repo_service.repository.StockReserveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RepoService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StockReserveRepository reserveRepository;

    /** Phase 1: Kiểm tra tồn kho và RESERVE (giữ chỗ hàng) */
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            Product product = productRepository.findByProductCode(event.getProductId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm: " + event.getProductId()));

            if (product.hasEnoughStock(event.getQuantity())) {
                StockReserve reserve = StockReserve.builder()
                        .orderId(event.getOrderId())
                        .productCode(event.getProductId())
                        .quantity(event.getQuantity())
                        .status("PENDING")
                        .build();
                reserveRepository.save(reserve);
                log.info("✅ Reserve kho thành công cho đơn: {}", event.getOrderId());
                sendStatus("repo-success-topic", event.getOrderId(), "SUCCESS", "Stock reserved");
            } else {
                log.warn("❌ Hết hàng cho đơn: {}", event.getOrderId());
                sendStatus("repo-fail-topic", event.getOrderId(), "FAILED", "Out of stock");
            }
        } catch (Exception e) {
            log.error("💥 Lỗi Reserve kho cho đơn {}: {}", event.getOrderId(), e.getMessage());
            sendStatus("repo-fail-topic", event.getOrderId(), "FAILED", "System error: " + e.getMessage());
        }
    }

    /** Phase 2: COMMIT hoặc ROLLBACK dựa trên phán quyết Ship */
    @Transactional
    public void finalizeOrder(String orderId, String status) {
        Optional<StockReserve> reserveOpt = reserveRepository.findByOrderId(orderId);
        if (reserveOpt.isEmpty()) {
            log.info("ℹ️ [EXIT] Không có bản ghi giữ chỗ cho đơn {}. Bỏ qua.", orderId);
            return;
        }
        StockReserve reserve = reserveOpt.get();

        if ("SUCCESS".equalsIgnoreCase(status)) {
            Product product = productRepository.findByProductCode(reserve.getProductCode())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm biến mất: " + reserve.getProductCode()));
            product.reduceStock(reserve.getQuantity());
            productRepository.save(product);
            log.info("🚚 [COMMIT] Trừ kho thật cho đơn {}", orderId);
        } else {
            log.warn("🔄 [ROLLBACK] Giải toả giữ chỗ hàng cho đơn: {}", orderId);
        }
        reserveRepository.delete(reserve);
    }

    private void sendStatus(String topic, String orderId, String status, String message) {
        kafkaTemplate.send(topic, RepoStatusEvent.builder()
                .orderId(orderId).status(status).message(message).build());
    }
}
```

#### `RepoConsumer.java`
```java
package com.example.repo_service.consumer;

import com.example.event_library.*;
import com.example.repo_service.service.RepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RepoConsumer {

    private final RepoService repoService;

    @KafkaListener(topics = "order-created", groupId = "repo-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("📦 [ORDER-CREATED] Nhận đơn {}. Sản phẩm: {}", event.getOrderId(), event.getProductId());
        repoService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "ship-fail", groupId = "repo-group")
    public void handleShipFail(ShipCreatedEvent event) {
        log.warn("🔄 [REPO-ROLLBACK] Giải toả hàng cho đơn: {}", event.getOrderId());
        repoService.finalizeOrder(event.getOrderId(), "FAIL");
    }

    @KafkaListener(topics = "ship-success", groupId = "repo-group")
    public void handleShipSuccess(ShipCreatedEvent event) {
        log.info("✅ [REPO-COMMIT] Trừ kho thật cho đơn: {}", event.getOrderId());
        repoService.finalizeOrder(event.getOrderId(), "SUCCESS");
    }
}
```

---

### ship-service

#### `ShippingSnapshot.java` (Entity)
```java
package com.example.ship_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShippingSnapshot {
    @Id
    private String orderId;

    private String payStatus  = "PENDING";  // PENDING, SUCCESS, FAILED
    private String repoStatus = "PENDING";  // PENDING, SUCCESS, FAILED

    // Backup data để rollback nếu cần
    private String userId;
    private Double amount;
    private String productId;
    private Integer quantity;

    @Version
    private Long version; // Optimistic lock

    public boolean isFinished()   { return !payStatus.equals("PENDING") && !repoStatus.equals("PENDING"); }
    public boolean isAllSuccess() { return payStatus.equals("SUCCESS")  && repoStatus.equals("SUCCESS"); }
}
```

#### `ShippingService.java`
```java
package com.example.ship_service.service;

import com.example.event_library.*;
import com.example.ship_service.entity.ShippingSnapshot;
import com.example.ship_service.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Arbitrator: Quyết định SUCCESS hay FAIL khi cả Pay và Repo đã phản hồi */
    @Transactional
    public void checkAndFinalize(String orderId) {
        ShippingSnapshot sn = repository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Snapshot not found: " + orderId));

        if (!sn.isFinished()) {
            log.info("⏳ Chờ thêm dữ liệu cho đơn {}: Pay={}, Repo={}", orderId, sn.getPayStatus(), sn.getRepoStatus());
            return;
        }

        if (sn.isAllSuccess()) {
            log.info("✅ Đơn {} THÀNH CÔNG!", orderId);
            sendStatus("ship-success", orderId, "SUCCESS", "Đơn hàng đã sẵn sàng giao.");
        } else {
            String reason = ("FAILED".equals(sn.getPayStatus()) ? "Lỗi thanh toán. " : "")
                          + ("FAILED".equals(sn.getRepoStatus()) ? "Lỗi kho bãi." : "");
            log.warn("❌ Đơn {} THẤT BẠI. Lý do: {}", orderId, reason);
            sendStatus("ship-fail", orderId, "FAILED", reason);
        }
    }

    private void sendStatus(String topic, String orderId, String status, String message) {
        ShipCreatedEvent event = ShipCreatedEvent.builder()
                .orderId(orderId)
                .status(status)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        kafkaTemplate.send(topic, event);
        log.info("🚀 Phát event {} tới topic {}", status, topic);
    }
}
```

#### `ShippingConsumer.java`
```java
package com.example.ship_service.consumer;

import com.example.event_library.*;
import com.example.ship_service.entity.ShippingSnapshot;
import com.example.ship_service.repository.ShippingRepository;
import com.example.ship_service.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.function.Consumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShippingConsumer {

    private final ShippingRepository repository;
    private final ShippingService shippingService;

    @KafkaListener(topics = "order-created", groupId = "ship-group")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("📝 Tạo Snapshot cho đơn: {}", event.getOrderId());
        ShippingSnapshot sn = ShippingSnapshot.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.getTotalPrice())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .payStatus("PENDING")
                .repoStatus("PENDING")
                .build();
        repository.save(sn);
    }

    @KafkaListener(topics = "pay-success-topic", groupId = "ship-group")
    public void onPaySuccess(PayStatusEvent event)  { updateAndCheck(event.getOrderId(), sn -> sn.setPayStatus("SUCCESS")); }

    @KafkaListener(topics = "pay-fail-topic", groupId = "ship-group")
    public void onPayFail(PayStatusEvent event)     { updateAndCheck(event.getOrderId(), sn -> sn.setPayStatus("FAILED")); }

    @KafkaListener(topics = "repo-success-topic", groupId = "ship-group")
    public void onRepoSuccess(RepoStatusEvent event){ updateAndCheck(event.getOrderId(), sn -> sn.setRepoStatus("SUCCESS")); }

    @KafkaListener(topics = "repo-fail-topic", groupId = "ship-group")
    public void onRepoFail(RepoStatusEvent event)   { updateAndCheck(event.getOrderId(), sn -> sn.setRepoStatus("FAILED")); }

    /** Cập nhật trạng thái và gọi trọng tài phân xử */
    private void updateAndCheck(String orderId, Consumer<ShippingSnapshot> updater) {
        repository.findById(orderId).ifPresentOrElse(sn -> {
            updater.accept(sn);
            repository.save(sn);
            shippingService.checkAndFinalize(orderId);
        }, () -> log.error("❌ Không tìm thấy Snapshot cho đơn: {}", orderId));
    }
}
```
