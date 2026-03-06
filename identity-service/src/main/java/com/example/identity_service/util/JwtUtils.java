package com.example.identity_service.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
@Component
public class JwtUtils {
    @Value("${app.jwt.secret}")
    private String secretKey;

    private Key getSigningKey(){
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
    public String generateToken(String username){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime()+86400000);

        System.out.println("---Đang tạo token---");
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(),SignatureAlgorithm.HS256)
                .compact();
    }
    public boolean validateToken(String token){
        try{
            System.out.println("---Đang kiểm tra token---");
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException e) {
            System.out.println("❌ Vé rách (Sai định dạng)");
        } catch (ExpiredJwtException e) {
            System.out.println("❌ Vé hết hạn (CPU check thấy time > exp)");
        } catch (SignatureException e) {
            System.out.println("❌ Chữ ký sai (Có thể bị Hacker sửa nội dung)");
        } catch (IllegalArgumentException e) {
            System.out.println("❌ Vé trống");
        }
        return false;
    }
    public String getUsernameFromToken(String token){
        System.out.println("---Đang lấy username---");
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
