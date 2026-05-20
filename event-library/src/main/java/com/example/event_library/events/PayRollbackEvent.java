package com.example.event_library.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayRollbackEvent {
    private String orderId;
    private String userId;
    private Double amount;
    private String reason; // Ví dụ: "REPO_OUT_OF_STOCK"
}
