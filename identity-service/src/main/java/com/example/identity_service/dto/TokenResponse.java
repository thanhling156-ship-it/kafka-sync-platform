package com.example.identity_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String tokenType = "Bearer";

    // Constructor này để dùng cho new TokenResponse(token) ở trên
    public TokenResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}
