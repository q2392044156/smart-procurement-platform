package com.procuremind.platform.service;

import com.procuremind.platform.anno.CurrentUser;

import com.procuremind.platform.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.time.Duration;
import org.springframework.data.redis.core.ScanOptions;
import com.procuremind.platform.pojo.UserRecord;
import com.procuremind.platform.pojo.TokenPair;

/**
 * 登录会话：校验口令、签发 access / refresh 令牌，并用 Redis 维护刷新白名单与登录失败计数。
 *
 * <p>refresh 令牌一次性使用：验签通过后先删 {@code refresh:<jti>}，删除成功才签发新的一对，旧令牌无法重放。
 * 登录失败按小写用户名累计，窗口从首次失败起算 15 分钟，满 5 次直接 429；成功登录即清零。
 * 本类不写用户数据，改密、停用、改角色后的强制下线由 {@link SystemManagementService} 调
 * {@link #revokeUserSessions(Long)} 触发。</p>
 *
 * <p>依赖：UserDirectory（用户与权限读取）、PasswordEncoder（BCrypt）、JwtService（签发与验签）、
 * Redis（失败计数 + 刷新白名单）。</p>
 */
@Service
public class AuthService {
    private final UserDirectory users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final StringRedisTemplate redis;
    public AuthService(UserDirectory users, PasswordEncoder encoder, JwtService jwt, StringRedisTemplate redis) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.redis = redis;
    }
    /**
     * 登录：命中失败阈值时不再校验密码，直接 429；账号不存在与密码错误走同一个计数器、同一句提示，
     * 成功则清零并返回重新装配身份后的令牌对。
     */
    public TokenPair login(String username, String password) {
        String failureKey = "login:fail:" + username.trim().toLowerCase(java.util.Locale.ROOT);
        String failures = redis.opsForValue().get(failureKey);
        if (failures != null && Long.parseLong(failures) >= 5) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请15分钟后重试");
        UserRecord row;
        try { row = users.byUsername(username); }
        catch (BusinessException e) { recordFailure(failureKey); throw e; }
        if (!row.enabled() || !encoder.matches(password, row.passwordHash())) { recordFailure(failureKey); throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"); }
        redis.delete(failureKey);
        return issue(users.current(row.id()));
    }
    /** 只在首次自增时设 TTL：窗口从第一次失败起算，后续失败不会把锁定时间越拖越长。 */
    private void recordFailure(String key) { Long count=redis.opsForValue().increment(key);if(count!=null&&count==1)redis.expire(key,Duration.ofMinutes(15)); }
    /**
     * 刷新：必须是 type=refresh 且 Redis 里 jti 仍在的令牌，删键成功才代表本次使用有效（旋转 + 防重放）；
     * 验签异常统一翻成 401，不把 JWT 的原始报错透给前端。
     */
    public TokenPair refresh(String refreshToken) {
        try {
            Claims claims = jwt.parse(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class)) || !Boolean.TRUE.equals(redis.delete("refresh:" + claims.getId())))
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新令牌已失效");
            return issue(users.current(Long.valueOf(claims.getSubject())));
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "刷新令牌无效"); }
    }
    /** 登出：尽力而为地删白名单键，令牌已损坏也静默成功，避免客户端因脏令牌卡在登录页。 */
    public void logout(String refreshToken) {
        try { redis.delete("refresh:" + jwt.parse(refreshToken).getId()); } catch (Exception ignored) {}
    }
    /**
     * 强制下线：SCAN 全量刷新键、删除值为该用户 id 的记录。白名单没有用户维度索引，只能全量扫，
     * 代价随在线登录量线性增长。
     */
    public void revokeUserSessions(Long userId) {
        try (var cursor = redis.scan(ScanOptions.scanOptions().match("refresh:*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (userId.toString().equals(redis.opsForValue().get(key))) redis.delete(key);
            }
        }
    }
    /**
     * 签发一对令牌并登记白名单：refresh 要再解析一次才拿得到 jti（jti 是在 token() 里生成的）；
     * expiresIn 固定 1800 秒，对应 access token 默认 30 分钟的配置。
     */
    private TokenPair issue(CurrentUser user) {
        String access = jwt.accessToken(user), refresh = jwt.refreshToken(user);
        Claims claims = jwt.parse(refresh);
        redis.opsForValue().set("refresh:" + claims.getId(), user.id().toString(), jwt.refreshTtl());
        return new TokenPair(access, refresh, 1800, user);
    }
    
}
