package com.example.identity_service.config;


import com.example.identity_service.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;


//Chỉ có nhiệm vụ lắp ráp các Bean, đưa ra công cụ(DAO) cuối cùng để sử dụng
@Configuration
public class ApplicationConfig {

    private final UserDetailsServiceImpl userDetailsService;

    public ApplicationConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Truyền userDetailsService vào constructor ngay khi khởi tạo
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);

        // PasswordEncoder thì vẫn có hàm Set (vì nó không phải là biến final trong mã nguồn)
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }
}
