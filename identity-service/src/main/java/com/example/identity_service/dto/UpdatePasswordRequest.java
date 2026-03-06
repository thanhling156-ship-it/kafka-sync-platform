package com.example.identity_service.dto;


import lombok.Data;

@Data
public class UpdatePasswordRequest {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
