package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
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
 *
 * <p>前缀由各业务模块传入（PR、PO、GRN、CT、KB 等），本类不校验其合法性；Redis 不可用时统一抛 503，
 * 由调用方的事务决定是否中断建单。</p>
 */
@Service
public class DocumentNumberService {
    /** 业务日固定按北京时间切分：服务器跑在 UTC 时，晚上 8 点后的单据不会被算到前一天。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public DocumentNumberService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 取下一个单号：INCR 与首次自增设 TTL 是两步，但序号正确性不依赖 TTL，TTL 只负责回收旧 key；
     * 并发不撞号靠 Redis 单命令的原子性，因此多实例部署也安全。Redis 故障统一翻成 503，让建单整体失败。
     */
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
