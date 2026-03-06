package com.example.identity_service.filter;

//Đã có thư viện riêng cho IdentityHeaderFilter
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collections;
import java.util.List;

@Component
public class IdentityHeaderFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String username = request.getHeader("X-User-Name");
            String role = request.getHeader("X-User-Role");

            if (username != null) {
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority(role != null ? role : "ROLE_USER")
                );
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().getAuthentication();
            }

            filterChain.doFilter(request, response);
        }
        catch(Exception e){
            // IN RA LỖI THỰC SỰ TRONG CONSOLE
            System.err.println("!!! LỖI XẢY RA SAU KHI QUA FILTER: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
