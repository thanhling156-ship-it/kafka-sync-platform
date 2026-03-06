package com.example.identity_service.dto;

import com.example.identity_service.entity.UserRole;

import lombok.Data;

@Data // Dùng Lombok cho gọn
public class RegisterDTO {

    // Không cần id ở đây vì Client không được phép tự chọn ID khi đăng ký
    private String username;

    private String password;

    // Không dùng @Enumerated ở đây
    private UserRole role;

    private String email;
}
