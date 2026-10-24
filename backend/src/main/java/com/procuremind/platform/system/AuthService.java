package com.procuremind.platform.system;

import com.procuremind.platform.common.BusinessException;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.time.Duration;

@Service
public class AuthService {
    private final UserDirectory users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final StringRedisTemplate redis;
    public AuthService(UserDirectory users, PasswordEncoder encoder, JwtService jwt, StringRedisTemplate redis) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.redis = redis;
    }
    public TokenPair login(String username, String password) {
        String failureKey = "login:fail:" + username.trim().toLowerCase(java.util.Locale.ROOT);
        String failures = redis.opsForValue().get(failureKey);
        if (failures != null && Long.parseLong(failures) >= 5) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请15分钟后重试");
        UserDirectory.UserRecord row;
        try { row = users.byUsername(username); }
        catch (BusinessException e) { recordFailure(failureKey); throw e; }
        if (!row.enabled() || !encoder.matches(password, row.passwordHash())) { recordFailure(failureKey); throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"); }
        redis.delete(failureKey);
        return issue(users.current(row.id()));
    }
    private void recordFailure(String key) { Long count=redis.opsForValue().increment(key);if(count!=null&&count==1)redis.expire(key,Duration.ofMinutes(15)); }
    public TokenPair refresh(String refreshToken) {
        try {
            Claims claims = jwt.parse(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class)) || !Boolean.TRUE.equals(redis.delete("refresh:" + claims.getId())))
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新令牌已失效");
            return issue(users.current(Long.valueOf(claims.getSubject())));
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新令牌无效"); }
    }
    public void logout(String refreshToken) {
        try { redis.delete("refresh:" + jwt.parse(refreshToken).getId()); } catch (Exception ignored) {}
    }
    private TokenPair issue(CurrentUser user) {
        String access = jwt.accessToken(user), refresh = jwt.refreshToken(user);
        Claims claims = jwt.parse(refresh);
        redis.opsForValue().set("refresh:" + claims.getId(), user.id().toString(), jwt.refreshTtl());
        return new TokenPair(access, refresh, 1800, user);
    }
    public record TokenPair(String accessToken, String refreshToken, long expiresIn, CurrentUser user) {}
}
