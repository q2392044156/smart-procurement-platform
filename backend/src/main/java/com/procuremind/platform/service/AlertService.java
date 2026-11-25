package com.procuremind.platform.service;

import com.procuremind.platform.mapper.AlertMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 到期与超时提醒：供应商资质临期/过期、订单交付临期/逾期、审批积压。
 *
 * <p>只做查询，不做推送；Agent 或看板按需拉取后自行组织提醒话术。结果不落库、不去重、不缓存，
 * 同一窗口参数重复拉取结果一致。三个时间窗口都会夹到合理区间，防止调用方传极端值拖垮查询。</p>
 */
@Service
public class AlertService {
    private final AlertMapper mapper;

    public AlertService(AlertMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 一次返回三类提醒：窗口分别夹在 0~365 天与 1~720 小时；overdueCount 由 daysLeft &lt; 0 的行数统计
     * （资质与交付），审批积压没有“逾期”概念固定为 0。外层 key 顺序固定，返回结构可直接当契约用。
     */
    public Map<String, Object> summary(int qualificationDays, int deliveryDays, int approvalHours) {
        int qualificationWindow = clamp(qualificationDays, 0, 365);
        int deliveryWindow = clamp(deliveryDays, 0, 365);
        int approvalWindow = clamp(approvalHours, 1, 720);

        List<Map<String, Object>> qualifications = normalizeDays(mapper.expiringQualifications(qualificationWindow));
        List<Map<String, Object>> deliveries = normalizeDays(mapper.dueDeliveries(deliveryWindow));
        List<Map<String, Object>> approvals = mapper.staleApprovals(approvalWindow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("qualifications", block("withinDays", qualificationWindow, qualifications,
                qualifications.stream().filter(row -> ((Number) row.get("daysLeft")).intValue() < 0).count(),
                "供应商资质临期或已过期"));
        result.put("deliveries", block("withinDays", deliveryWindow, deliveries,
                deliveries.stream().filter(row -> ((Number) row.get("daysLeft")).intValue() < 0).count(),
                "订单交付临期或已逾期"));
        result.put("approvals", block("olderThanHours", approvalWindow, approvals, 0L, "审批积压"));
        return result;
    }

    /**
     * 把 {@code datediff()} 的结果统一成 int。
     *
     * <p>同一条 SQL 在不同环境可能被驱动读成 {@code Long}（BIGINT）或 {@code String}，
     * 而全局 Jackson 又把 Long 写成字符串——于是同一个字段有时是 {@code "3"}、有时是 {@code 3}，
     * 契约和前端渲染都会抖。这里定死成数值，顺带让 {@code daysLeft < 0} 的判定不再依赖转型。</p>
     */
    private static List<Map<String, Object>> normalizeDays(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("daysLeft", daysLeft(row.get("daysLeft")));
            return copy;
        }).toList();
    }

    /** 数值归一化的兜底：无法解析的脏数据按 0 天处理，宁可少报一条也不让整个看板 500。 */
    private static int daysLeft(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 三类提醒共用同一块结构（窗口、条数、逾期数、提示语、明细），前端与 Agent 只需一套渲染逻辑。 */
    private Map<String, Object> block(String windowKey, int window, List<Map<String, Object>> items, long overdue, String message) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put(windowKey, window);
        block.put("count", items.size());
        block.put("overdueCount", overdue);
        block.put("message", message);
        block.put("items", items);
        return block;
    }

    /** 窗口参数夹紧：调用方（尤其 Agent）可能传 0、负数或极大值，这里统一收敛到安全区间。 */
    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
