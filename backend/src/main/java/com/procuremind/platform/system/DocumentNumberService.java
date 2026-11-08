package com.procuremind.platform.system;

import com.procuremind.platform.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成：Redis 自增序列 + 日期分段，避免毫秒时间戳在并发下撞号。
 *
 * <p>格式：前缀 + yyyyMMdd + “-” + 4 位序号，例如 {@code PR20261108-0001}。
 * 序列按天分 key 并保留两天，跨天自动从 0001 重新开始。</p>
 */
@Service
public class DocumentNumberService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public DocumentNumberService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next(String prefix) {
        String day = LocalDate.now(BUSINESS_ZONE).format(DAY);
        String key = "seq:" + prefix + ":" + day;
        Long value;
        try {
            value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) redis.expire(key, Duration.ofDays(2));
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "单号生成服务不可用");
        }
        if (value == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "单号生成失败");
        return prefix + day + "-" + String.format("%04d", value);
    }
}
