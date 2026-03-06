package com.example.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class CommonAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String username = request.getHeader("X-User-Name");
        String role = request.getHeader("X-User-Role");

        if (username != null) {
            // 1. Nạp cho Spring Security
            var auth = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority(role != null ? role : "ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // 2. Nạp cho UserContext của bạn (Dùng hàm vừa tạo)
            UserContext.setCurrentUser(username);
            UserContext.setCurrentRole(role);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // 3. Xóa dữ liệu khi kết thúc request để an toàn
            UserContext.clear();
        }
    }
}