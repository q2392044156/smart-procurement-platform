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
 * <p>只做查询，不做推送；Agent 或看板按需拉取后自行组织提醒话术。</p>
 */
@Service
public class AlertService {
    private final AlertMapper mapper;

    public AlertService(AlertMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> summary(int qualificationDays, int deliveryDays, int approvalHours) {
        int qualificationWindow = clamp(qualificationDays, 0, 365);
        int deliveryWindow = clamp(deliveryDays, 0, 365);
        int approvalWindow = clamp(approvalHours, 1, 720);

        List<Map<String, Object>> qualifications = mapper.expiringQualifications(qualificationWindow);
        List<Map<String, Object>> deliveries = mapper.dueDeliveries(deliveryWindow);
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

    private Map<String, Object> block(String windowKey, int window, List<Map<String, Object>> items, long overdue, String message) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put(windowKey, window);
        block.put("count", items.size());
        block.put("overdueCount", overdue);
        block.put("message", message);
        block.put("items", items);
        return block;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
