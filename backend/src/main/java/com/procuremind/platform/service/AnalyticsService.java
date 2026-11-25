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
 * 订单类指标统一应用当前用户的数据范围，因此数据范围外的订单不计入任何分数与总额。
 *
 * <p>只读、不落库、不缓存；金额一律以十进制字符串输出，避免前端浮点误差。依赖 AnalyticsMapper 与
 * DataScopeService（拼接订单数据范围条件）。</p>
 */
@Service
public class AnalyticsService {
    private final AnalyticsMapper mapper;
    private final DataScopeService dataScope;

    public AnalyticsService(AnalyticsMapper mapper, DataScopeService dataScope) {
        this.mapper = mapper;
        this.dataScope = dataScope;
    }

    /**
     * 供应商记分卡：年度 &lt;= 0 时取当前自然年；按订单数据范围统计交付、质量、价格等指标。
     * 供应商不存在（或不在可见范围）统一 404，不暴露“存在但你看不到”的差异。
     */
    public Map<String, Object> supplierScorecard(Long supplierId, int year) {
        int fiscalYear = fiscalYear(year);
        String scope = dataScope.purchaseOrderScope("o", "u");
        Map<String, Object> row = mapper.supplierScorecard(fiscalYear, scope, supplierId);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "供应商不存在");
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("year", fiscalYear);
        return result;
    }

    /**
     * 库存风险：净缺口 = max(0, 缺口 - 在途)，再据此分级——有净缺口 HIGH、在途可覆盖 COVERED、其余 LOW。
     * 直接改写 mapper 返回的行（MyBatis 每行是独立 Map），省一次拷贝；netGap 以字符串回传保证精度。
     */
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

    /**
     * 花费分析：groupBy 缺省 department，大小写与空白已归一，非法维度 400（白名单分支，不拼 SQL）。
     * total 在 Java 侧对结果行累加，保证与返回的 records 完全一致，不依赖数据库再算一次合计。
     */
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

    /** 年度归一化：0 或负数（前端“全部/不传”的落库形态）一律按当前自然年统计。 */
    private int fiscalYear(int year) {
        return year <= 0 ? LocalDate.now().getYear() : year;
    }

    /** 金额归一化：null 记 0；驱动返回 Double/String/Decimal 都能吃下，统一转 BigDecimal 再运算。 */
    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal big) return big;
        return new BigDecimal(value.toString());
    }
}
