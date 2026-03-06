package com.example.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter; // Dòng này cực kỳ quan trọng
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;         // Dòng này cực kỳ quan trọng
import reactor.core.publisher.Mono;

@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
				.route("identity-route", r -> r.path("/**") // Mở toang cửa
						.uri("lb://identity-service"))
				.build();
	}

	@Bean
	public GlobalFilter customGlobalFilter() {
		return (exchange, chain) -> {
			System.out.println(">>> [GATEWAY] NHẬN REQUEST: " + exchange.getRequest().getURI());
			return chain.filter(exchange);
		};
	}
}