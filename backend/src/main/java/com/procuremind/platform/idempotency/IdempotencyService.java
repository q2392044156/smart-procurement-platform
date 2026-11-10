package com.procuremind.platform.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 写操作幂等：同一用户、同一操作、同一请求内容在时间窗内只执行一次。
 *
 * <p>客户端显式传入 Idempotency-Key 时以该值为准；未传时按“操作 + 用户 + 请求内容”生成指纹，
 * 这样 Agent 重试同样的工具调用不会重复建单。失败请求会标记为 FAILED 并允许重试。</p>
 */
@Service
public class IdempotencyService {
    /** 自动指纹的有效期：超过该窗口的相同请求视为一次新的业务操作。 */
    private static final int AUTO_TTL_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> execute(String explicitKey, String operation, Object payload,
                                       Supplier<Map<String, Object>> action) {
        Long userId = SecurityUtils.currentUser().id();
        String key = explicitKey == null || explicitKey.isBlank()
                ? "auto:" + fingerprint(operation, userId, payload)
                : explicitKey.trim();
        boolean auto = explicitKey == null || explicitKey.isBlank();

        // 同一 Key 的并发请求不要立刻 409：等待正在执行的请求完成，返回同一结果。
        long deadline = System.currentTimeMillis() + 15_000;
        while (true) {
            try {
                jdbc.update("insert into sys_idempotency_record(idempotency_key,user_id,operation,status) values(?,?,?,'PROCESSING')",
                        key, userId, operation);
                return run(key, userId, operation, action);
            } catch (DuplicateKeyException duplicate) {
                List<Map<String, Object>> rows = jdbc.queryForList("""
                        select status,response_json,created_at,
                               (created_at < utc_timestamp() - interval ? minute) stale
                        from sys_idempotency_record
                        where idempotency_key=? and user_id=? and operation=?
                        """, AUTO_TTL_MINUTES, key, userId, operation);
                if (rows.isEmpty()) {
                    if (System.currentTimeMillis() >= deadline) break;
                    pause();
                    continue;
                }
                Map<String, Object> row = rows.getFirst();
                String status = String.valueOf(row.get("status"));
                boolean stale = ((Number) row.get("stale")).intValue() == 1;
                if ("COMPLETED".equals(status)) {
                    if (auto && stale) { remove(key, userId, operation); continue; }
                    return readResponse(row.get("response_json"));
                }
                if ("FAILED".equals(status) || stale) {
                    remove(key, userId, operation);
                    continue;
                }
                if (System.currentTimeMillis() >= deadline) break;
                pause();
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT, "相同请求仍在处理中，请稍后重试");
    }

    private void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.CONFLICT, "等待幂等结果被中断，请重试");
        }
    }

    private Map<String, Object> run(String key, Long userId, String operation, Supplier<Map<String, Object>> action) {
        Map<String, Object> result;
        try {
            result = action.get();
        } catch (RuntimeException failure) {
            jdbc.update("update sys_idempotency_record set status='FAILED' where idempotency_key=? and user_id=? and operation=?",
                    key, userId, operation);
            throw failure;
        }
        jdbc.update("update sys_idempotency_record set status='COMPLETED',response_json=? where idempotency_key=? and user_id=? and operation=?",
                writeResponse(result), key, userId, operation);
        return result;
    }

    private void remove(String key, Long userId, String operation) {
        jdbc.update("delete from sys_idempotency_record where idempotency_key=? and user_id=? and operation=?", key, userId, operation);
    }

    private String fingerprint(String operation, Long userId, Object payload) {
        try {
            String canonical = mapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((operation + "|" + userId + "|" + canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "请求指纹计算失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readResponse(Object json) {
        try {
            return json == null ? Map.of() : mapper.readValue(json.toString(), Map.class);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.CONFLICT, "幂等结果解析失败");
        }
    }

    private String writeResponse(Map<String, Object> result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "幂等结果序列化失败");
        }
    }
}
