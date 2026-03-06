package com.example.identity_service.service;

import com.example.identity_service.config.ApplicationConfig;
import com.example.identity_service.dto.RegisterDTO;
import com.example.identity_service.dto.UpdatePasswordRequest;
import com.example.identity_service.entity.User;
import com.example.identity_service.entity.UserRole;
import com.example.identity_service.repository.UserRepository;
import com.example.identity_service.util.JwtUtils;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityProducer identityProducer;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private JwtUtils utils;

    @Transactional
    public void updatePassword(String username, UpdatePasswordRequest request) {
        System.out.println("Username : "+username);
        // 1. Tìm User trong DB
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        // 2. Kiểm tra mật khẩu cũ (Máy bạn tự so khớp mã băm ở đây)
        if (!encoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Mật khẩu cũ không chính xác!");
        }
        //request.getOldPassword() là pass từ client
        //user.getPassword() là pass gốc
        // 3. Kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp nhau không
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu mới và xác nhận không khớp!");
        }
        //Double-check như thường

        // 4. Băm mật khẩu mới và lưu lại
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

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

    public String register(RegisterDTO dto){

        // 1. Kiểm tra xem username đã tồn tại chưa
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }

        // 2. Tạo đối tượng User mới
        //encode password trước khi lưu xuống DB
        String encodedPassword = encoder.encode(dto.getPassword());

        User newUser = new User();
        newUser.setUsername(dto.getUsername());
        newUser.setPassword(encodedPassword);
        newUser.setEmail(dto.getEmail());
        newUser.setRole(UserRole.BASIC);

        //Test
        User user = userRepository.save(newUser);

        identityProducer.publishUserRegistration(user);

        return "Đăng ký thành công";
    }
}

