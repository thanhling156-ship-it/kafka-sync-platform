package com.example.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter; // Dòng này cực kỳ quan trọng
import org.springframework.context.annotation.Bean;         // Dòng này cực kỳ quan trọng

@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@Bean
	public GlobalFilter customGlobalFilter() {
		return (exchange, chain) -> {
			System.out.println(">>> [GATEWAY] NHẬN REQUEST: " + exchange.getRequest().getURI());
			return chain.filter(exchange);
		};
	}
}