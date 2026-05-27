package com.huang.parkingshare.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, Integer role, String phone) {
        String jti = UUID.randomUUID().toString();  // 唯一标识
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("phone", phone);
        claims.put("jti", jti);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public String getJtiFromToken(String token) {
        return parseToken(token).get("jti",String.class);
    }


    /**
     * 获取 Token 的绝对过期时间（毫秒时间戳）
     * @param token JWT 字符串
     * @return 过期时间的毫秒值，若解析失败返回 -1
     */
    public  long getExpirationFromToken(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return expiration != null ? expiration.getTime() : -1L;
        } catch (Exception e) {
            // 解析失败（Token 无效、签名错误、已过期等）
            return -1L;
        }
    }

    /**
     * 获取 Token 剩余有效时间（毫秒）
     * @param token JWT 字符串
     * @return 剩余毫秒数（正数表示未过期，0或负数表示已过期或无效），若无法解析返回 -1
     */
    public  long getRemainingTimeMillis(String token) {
        long expiration = getExpirationFromToken(token);
        if (expiration == -1L) {
            return -1L; // Token 无效
        }
        long now = System.currentTimeMillis();
        return expiration - now;
    }

    public long getExpiration() {
        return expiration;
    }
}