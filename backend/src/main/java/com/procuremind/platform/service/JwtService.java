package com.procuremind.platform.service;

import com.procuremind.platform.anno.CurrentUser;

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

/**
 * JWT 签发与校验：HS256 对称签名，access / refresh 两类令牌靠 {@code type} 声明区分。
 *
 * <p>本类只回答「签名对不对、过没过期」，令牌是否仍被允许使用由 {@link AuthService} 的 Redis 白名单决定，
 * 能验签不等于仍有效。密钥取自 {@code procuremind.security.jwt-secret}，不足 32 字节时
 * {@code Keys.hmacShaKeyFor} 会在启动期直接失败，不会带着弱密钥跑起来。</p>
 *
 * <p>依赖：仅配置项 {@code procuremind.security.*}，不碰数据库与 Redis；验签异常由调用方翻成 401。</p>
 */
@Service
public class JwtService {
    /** HS256 密钥：由配置派生，轮换后所有已签发令牌立即失效。 */
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
    /** 短期访问令牌：subject 是用户 id，另带 username 与 type=access，供鉴权过滤器直接取用。 */
    public String accessToken(CurrentUser user) { return token(user, accessTtl, "access"); }
    /** 长期刷新令牌：jti 会被 AuthService 登记成一次性白名单键，用后即删。 */
    public String refreshToken(CurrentUser user) { return token(user, refreshTtl, "refresh"); }
    /**
     * 两类令牌只差 TTL 与 type 声明；jti 用随机 UUID，保证 Redis 白名单键不会因同秒签发而互相覆盖。
     */
    private String token(CurrentUser user, Duration ttl, String type) {
        Instant now = Instant.now();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(user.id().toString())
                .claim("username", user.username()).claim("type", type)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }
    /** 验签并校验过期；失败抛 io.jsonwebtoken 的运行时异常，不查 Redis，也不吞异常。 */
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    /** 暴露刷新令牌有效期，供 AuthService 设置白名单 TTL，避免同一配置在两处各写一份。 */
    public Duration refreshTtl() { return refreshTtl; }
}
