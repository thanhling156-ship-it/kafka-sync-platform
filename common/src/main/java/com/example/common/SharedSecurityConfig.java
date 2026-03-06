package com.example.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharedSecurityConfig {
    @Bean
    @ConditionalOnMissingBean // Chỉ tạo nếu Service đó chưa có Filter này
    public CommonAuthFilter commonAuthFilter() {
        return new CommonAuthFilter();
    }
}
