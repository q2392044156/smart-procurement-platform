package com.procuremind.platform.system;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    public JwtService(@Value("${procuremind.security.jwt-secret}") String secret,
                      @Value("${procuremind.security.access-token-minutes}") long accessMinutes,
                      @Value("${procuremind.security.refresh-token-days}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }
    public String accessToken(CurrentUser user) { return token(user, accessTtl, "access"); }
    public String refreshToken(CurrentUser user) { return token(user, refreshTtl, "refresh"); }
    private String token(CurrentUser user, Duration ttl, String type) {
        Instant now = Instant.now();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(user.id().toString())
                .claim("username", user.username()).claim("type", type)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public Duration refreshTtl() { return refreshTtl; }
}
