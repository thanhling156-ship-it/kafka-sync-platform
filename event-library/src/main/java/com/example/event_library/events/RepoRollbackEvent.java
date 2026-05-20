package com.example.event_library.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepoRollbackEvent {
    private String orderId;
    private String reason; // Lý do hoàn tác (ví dụ: "PAYMENT_FAILED", "SHIP_FAIL")
}
