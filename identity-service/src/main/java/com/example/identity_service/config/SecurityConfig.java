package com.example.identity_service.config;


import com.example.common.CommonAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CommonAuthFilter commonAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Các API login/register thì không cần check Token/Header
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Các API khác (như update-password) thì bắt buộc phải có Token/Header
                        .anyRequest().authenticated()
                )
                // QUAN TRỌNG NHẤT: Phải có dòng này thì CommonAuthFilter mới chạy!
                .addFilterBefore(commonAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
} // <--- Dấu này phải ở CUỐI CÙNG của file