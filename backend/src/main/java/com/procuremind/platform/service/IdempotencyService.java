package com.procuremind.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.IdempotencyMapper;
import com.procuremind.platform.utils.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
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
 *
 * <p>状态机：PROCESSING → COMPLETED（存响应快照，重复请求原样重放）或 FAILED（删记录，允许重试）。
 * 唯一键冲突不代表失败，而是“已有同 key 请求在跑”，故冲突后轮询等待最多 15 秒再决定重放还是 409。
 * 本类只管幂等记录，不负责业务事务边界——记录与业务是否同事务取决于调用方。</p>
 */
@Service
public class IdempotencyService {
    /** 自动指纹的有效期：超过该窗口的相同请求视为一次新的业务操作。 */
    private static final int AUTO_TTL_MINUTES = 10;

    private final IdempotencyMapper records;
    private final ObjectMapper json;

    public IdempotencyService(IdempotencyMapper records, ObjectMapper json) {
        this.records = records;
        this.json = json;
    }

    /**
     * 幂等执行入口：显式 key 去空白后直接用，未传则用 auto: 前缀的指纹；先插 PROCESSING 占位，
     * 再执行业务并把响应快照与 COMPLETED 一起落库。唯一键冲突时进入等待循环——已完成则重放上次结果，
     * 已失败或已过期则删记录重试，仍在处理则每 100ms 轮询，超过 15 秒按 409 让调用方稍后再试。
     */
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
                records.insertProcessing(key, userId, operation);
                return run(key, userId, operation, action);
            } catch (DuplicateKeyException duplicate) {
                List<Map<String, Object>> rows = records.find(AUTO_TTL_MINUTES, key, userId, operation);
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

    /** 轮询间隔 100ms：足够短以免拖慢重试，又不至于把库打满；中断时恢复标志位并转 409，不吞异常。 */
    private void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.CONFLICT, "等待幂等结果被中断，请重试");
        }
    }

    /**
     * 执行业务并落状态：失败先写 FAILED 再把原始异常原样抛出（调用方要看到真实错误码），
     * 成功则把响应 JSON 与 COMPLETED 一次写入，供后续重复请求重放。
     */
    private Map<String, Object> run(String key, Long userId, String operation, Supplier<Map<String, Object>> action) {
        Map<String, Object> result;
        try {
            result = action.get();
        } catch (RuntimeException failure) {
            records.markFailed(key, userId, operation);
            throw failure;
        }
        records.markCompleted(key, userId, operation, writeResponse(result));
        return result;
    }

    /** 删除幂等记录：只用于“这条记录已失效”（FAILED、超过 TTL、自动指纹过期），删掉才能重新插入。 */
    private void remove(String key, Long userId, String operation) {
        records.remove(key, userId, operation);
    }

    /**
     * 生成请求指纹：canonical JSON 必须按 key 排序（否则同一 Map 不同遍历顺序会算出两个指纹），
     * 再拼上操作名与用户 id 做 SHA-256，只取前 16 字节十六进制，够用且好排查。
     */
    private String fingerprint(String operation, Long userId, Object payload) {
        try {
            String canonical = json.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((operation + "|" + userId + "|" + canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "请求指纹计算失败");
        }
    }

    /** 重放响应：null 视为空结果（历史记录可能没存正文）；解析失败按 409 而非 500，说明这条快照不可用。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readResponse(Object json) {
        try {
            return json == null ? Map.of() : this.json.readValue(json.toString(), Map.class);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.CONFLICT, "幂等结果解析失败");
        }
    }

    /** 序列化响应快照：失败按 500 终止，绝不能存半条 JSON 让下次重放出错。 */
    private String writeResponse(Map<String, Object> result) {
        try {
            return json.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "幂等结果序列化失败");
        }
    }
}
