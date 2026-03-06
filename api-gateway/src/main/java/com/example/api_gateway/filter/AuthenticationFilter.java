package com.example.api_gateway.filter;


import com.example.api_gateway.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

//Sharing immutable state = nhiều object reference cùng một dữ liệu immutable để tránh copy và vẫn an toàn
//nhiều object có thể share reference tới cùng immutable state, và chỉ tạo state mới cho phần bị thay đổi
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtils jwtUtils;

    // Danh sách các API không cần check Token (Vùng xanh)
    private static final List<String> EXCLUDE_URLS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password"
    );

    @Override
    public int getOrder() {
        // Độ ưu tiên cao nhất (-1) để nó chạy trước mọi Filter khác
        return -1;
    }

    @Override//đây là hàm biến đổi, từ token-> dữ liệu có nghĩa
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        String path = exchange.getRequest().getURI().getPath();

        if(path.contains("/auth/login") || path.contains("/auth/register")){
            return chain.filter(exchange);
        }


        System.out.println(">>> GATEWAY NHẬN ĐƯỢC HEADER: " + authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println(">>> LỖI: KHÔNG TÌM THẤY TOKEN!");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        // chỉ xuống đến đây nếu path thuộc loại có token
        if(authHeader != null || authHeader.startsWith("Bearer ")){
            String token = authHeader.substring(7);
            try{
                Claims claims = jwtUtils.getClaimsFromToken(token);

                //ServerHttpRequest request là bản sao sinh ra từ ngăn tủ exchange.getRequest()
                //sau đó được sửa qua .header() cho phù hợp
                //.build() để đúc

                //=> tạo request mới từ request cũ và thêm header
                ServerHttpRequest request = exchange.getRequest().mutate()
                        .header("X-User-Name",claims.getSubject())
                        .header("X-User-Role",String.valueOf(claims.get("role")))
                        .build();

                //đây là bước rút request cũ ra để thay request mới vào 1 exchange mới từ việc exchange.mutate()

                //=> tạo exchange mới chứa request mới
                // rồi truyền xuống filter chain
                return chain.filter(exchange.mutate().request(request).build());
            }
            catch (Exception e) {
                // Token lỏ hoặc hết hạn -> Chặn đứng, trả về 401
                return onError(exchange, "Token invalid or expired", HttpStatus.UNAUTHORIZED);
            }
        }
        // Không có Token mà đòi vào vùng cấm -> Chặn đứng
        return onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
    }
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}
