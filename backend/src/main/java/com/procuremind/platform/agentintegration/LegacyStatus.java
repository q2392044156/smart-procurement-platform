package com.procuremind.platform.agentintegration;

import com.procuremind.platform.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * 旧 MCP 协议的状态口径适配。
 *
 * <p>入参同时接受历史数字编码（0-4）与平台状态字符串；出参统一为平台状态字符串，
 * 避免模型在“数字状态 / 字符串状态”之间来回猜测。</p>
 */
public final class LegacyStatus {
    private LegacyStatus() {}

    private static final Map<String, String> NUMERIC = Map.of(
            "0", "DRAFT",
            "1", "APPROVED",
            "2", "PARTIALLY_RECEIVED",
            "3", "RECEIVED",
            "4", "CLOSED");

    private static final Set<String> ORDER_STATUSES = Set.of(
            "DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED",
            "PARTIALLY_RECEIVED", "RECEIVED", "CLOSED", "CANCELLED");

    /** 列表筛选：空值表示不过滤；非法值返回 400，而不是静默查出空列表。 */
    public static String filter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toUpperCase();
        if (NUMERIC.containsKey(value)) return NUMERIC.get(value);
        if (!ORDER_STATUSES.contains(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的订单状态：" + raw);
        }
        return value;
    }

    /** 状态变更：只允许“提交审批”和“关闭已收货订单”，其余明确拒绝。 */
    public static Decision decision(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if (Set.of("1", "SUBMIT", "PENDING_APPROVAL").contains(value)) return Decision.SUBMIT;
        if (Set.of("4", "CLOSE", "CLOSED", "COMPLETED").contains(value)) return Decision.CLOSE;
        throw new BusinessException(HttpStatus.CONFLICT, "Agent不能绕过审批或收货流程直接修改该状态");
    }

    public enum Decision { SUBMIT, CLOSE }
}
