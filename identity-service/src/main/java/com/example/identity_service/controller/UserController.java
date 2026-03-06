package com.example.identity_service.controller;

import com.example.common.UserContext;
import com.example.identity_service.dto.LoginDTO;
import com.example.identity_service.dto.RegisterDTO;
import com.example.identity_service.dto.TokenResponse;
import com.example.identity_service.dto.UpdatePasswordRequest;
import com.example.identity_service.service.AuthService;
import com.example.identity_service.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    @Autowired
    private UserService userService;

    @PutMapping("/update-password")
    public ResponseEntity<?> updatePassword(
            @RequestBody UpdatePasswordRequest request
    )
    {
        String username = UserContext.getCurrentUser(); // Lấy username (sub) từ AccessToken
        userService.updatePassword(username, request);
        return ResponseEntity.ok("Đổi mật khẩu thành công!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO){
        System.out.println(">>> [IDENTITY-SERVICE] Đã nhận được request login!");
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        String token = userService.login(username,password);

        // TRẢ VỀ: Đối tượng chứa Token (TokenResponse)
        return ResponseEntity.ok(new TokenResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO registerDTO){
        System.out.println(">>> [IDENTITY-SERVICE] Đã nhận được request register!");

        String token = userService.register(registerDTO);
        // TRẢ VỀ: Đối tượng chứa Token (TokenResponse)
        return ResponseEntity.ok(new TokenResponse(token));
    }
}
