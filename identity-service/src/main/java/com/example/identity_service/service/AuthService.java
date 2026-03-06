package com.example.identity_service.service;

import com.example.identity_service.dto.RegisterDTO;
import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import com.example.identity_service.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

//Dùng để kiểm tra logic khi login lần đầu
@Service
public class AuthService {
    @Autowired
    private UserRepository repository;

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private JwtUtils utils;

    @Autowired
    private PasswordEncoder encoder;

    public String login(String username, String password){
        try {
            //Form/DTO cho manager để gửi cho hàm logic phù hợp
            //Đóng gói thành 1 hồ sơ
            UsernamePasswordAuthenticationToken requestToken =
                    new UsernamePasswordAuthenticationToken(username, password);
            //Chỉ để kiểm tra password
            //đây là lúc cho manager hồ sơ, sau đó manager tự đi tìm hàm phù hợp(DAO)
            Authentication resultToken = manager.authenticate(requestToken);

            return utils.generateToken(resultToken.getName());
        }
        catch(AuthenticationException e){
            throw new RuntimeException("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
    }

    public Long id(String username, String password){
        try {
            UsernamePasswordAuthenticationToken requestToken =
                    new UsernamePasswordAuthenticationToken(username, password);
            Authentication resultToken = manager.authenticate(requestToken);

            // Lấy username từ resultToken (đã được confirm là đúng)
            String authenticatedUsername = resultToken.getName();

            // Truy tìm ID trong PostgreSQL
            return repository.findShadowUser(authenticatedUsername)
                    .map(User::getId)
                    .orElseThrow(() -> new RuntimeException("Lỗi hy hữu: Xác thực xong nhưng không thấy User trong DB!"));
        }
        catch(AuthenticationException e){
            throw new RuntimeException("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
    }

    public void register(RegisterDTO dto){
        if(repository.existsByUsername(dto.getUsername())){
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }
        //encode password trước khi lưu xuống DB
        String encodedPassword = encoder.encode(dto.getPassword());

        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        newUser.setPassword(encodedPassword);
        // Trong file AuthService.java
        newUser.setRole(dto.getRole());// Gán quyền mặc định
        repository.save(newUser);
    }

    /*LỖi
    @Transactional
    public void updateProfile(String id, UpdatePasswordRequest updateDTO){
        if(repository.existsByUsername(updateDTO.getUsername())){
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }
    }
     */
}