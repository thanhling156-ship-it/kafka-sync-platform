package com.example.common.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRegistrationEvent {
    private String userId;
    private String username;
    private String email;
    private String fullName;    // Mới: Thông tin chi tiết cho Level 2
    private String phoneNumber; // Mới
    private String address;     // Mới
    private Instant createdAt;  // Mới: Thời điểm sự kiện xảy ra
}