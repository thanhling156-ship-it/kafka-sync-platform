package com.example.api_gateway.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {
    @Value("${jwt.secret}")
    private String secretKey;

    //Hàm lấy cả Claims
    public Claims getClaimsFromToken(String token) {
        try {
            // Đảm bảo secretKey đã được nạp đúng từ file .yml
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8))) // Thêm Charset để tránh lỗi font
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            System.out.println("THUC HIEN THANH CONG");
            return claims;     
        } catch (ExpiredJwtException e) {
            System.out.println(">>> [JWT ERROR] Token đã hết hạn: " + e.getMessage());
        } catch (SignatureException e) {
            System.out.println(">>> [JWT ERROR] Sai chữ ký! Secret Key ở Gateway không khớp với Identity.");
        } catch (MalformedJwtException e) {
            System.out.println(">>> [JWT ERROR] Token không đúng định dạng (sai cấu trúc 3 phần).");
        } catch (Exception e) {
            System.out.println(">>> [JWT ERROR] Lỗi không xác định: " + e.getMessage());
        }
        return null; // Trả về null để Filter biết mà chặn (401)
    }

    //Hàm lấy Username từ Claims
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    public boolean isTokenExpired(String token) {
        return getClaimsFromToken(token).getExpiration().before(new Date());
    }
}

