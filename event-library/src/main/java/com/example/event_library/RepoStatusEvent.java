package com.example.event_library;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepoStatusEvent {
    private String orderId;
    private String status; // SUCCESS hoặc FAILED
    private String message; // <--- THÊM DÒNG NÀY ĐỂ CHỨA LÝ DO
    private String productId;
    private Integer quantity;
}