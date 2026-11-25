package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.AnalyticsMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 分析工具的数据聚合层：供应商记分卡、库存风险、花费分析。
 *
 * <p>聚合放在服务端 SQL 中完成，避免模型多步取数后自行计算导致口径不一致；
 * 订单类指标统一应用当前用户的数据范围。</p>
 */
@Service
public class AnalyticsService {
    private final AnalyticsMapper mapper;
    private final DataScopeService dataScope;

    public AnalyticsService(AnalyticsMapper mapper, DataScopeService dataScope) {
        this.mapper = mapper;
        this.dataScope = dataScope;
    }

    public Map<String, Object> supplierScorecard(Long supplierId, int year) {
        int fiscalYear = fiscalYear(year);
        String scope = dataScope.purchaseOrderScope("o", "u");
        Map<String, Object> row = mapper.supplierScorecard(fiscalYear, scope, supplierId);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "供应商不存在");
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("year", fiscalYear);
        return result;
    }

    public List<Map<String, Object>> inventoryRisk() {
        List<Map<String, Object>> rows = mapper.inventoryRisk();
        for (Map<String, Object> row : rows) {
            BigDecimal shortage = decimal(row.get("shortage"));
            BigDecimal inTransit = decimal(row.get("inTransit"));
            BigDecimal netGap = shortage.subtract(inTransit).max(BigDecimal.ZERO);
            row.put("netGap", netGap.toPlainString());
            row.put("riskLevel", netGap.signum() > 0 ? "HIGH" : inTransit.signum() > 0 ? "COVERED" : "LOW");
        }
        return rows;
    }

    public Map<String, Object> spendAnalysis(int year, String groupBy) {
        int fiscalYear = fiscalYear(year);
        String group = groupBy == null || groupBy.isBlank() ? "department" : groupBy.trim().toLowerCase();
        String scope = dataScope.purchaseOrderScope("o", "u");
        List<Map<String, Object>> rows = switch (group) {
            case "department" -> mapper.spendByDepartment(fiscalYear, scope);
            case "supplier" -> mapper.spendBySupplier(fiscalYear, scope);
            case "category" -> mapper.spendByCategory(fiscalYear, scope);
            case "month" -> mapper.spendByMonth(fiscalYear, scope);
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的统计维度");
        };
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add(decimal(row.get("amount")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", fiscalYear);
        result.put("groupBy", group);
        result.put("total", total.toPlainString());
        result.put("records", rows);
        return result;
    }

    private int fiscalYear(int year) {
        return year <= 0 ? LocalDate.now().getYear() : year;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal big) return big;
        return new BigDecimal(value.toString());
    }
}
