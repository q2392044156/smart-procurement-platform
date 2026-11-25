package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.BudgetMapper;
import com.procuremind.platform.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门年度预算：申请提交与直采订单创建时校验可用额度。
 *
 * <p>口径：已占用 = 未取消/未驳回的采购申请预算额 + 无来源申请的直采订单金额。
 * 申请转订单不重复计算（订单 requisition_id 非空时不计入直采口径）。</p>
 *
 * <p>边界：只读校验，不预占额度、不写预算表；占用额由单据状态实时统计，因此并发提交时两个申请可能
 * 同时通过校验造成超支，最终以实际单据为准（不做行锁或额度冻结）。金额单位元，年度按自然年。</p>
 */
@Service
public class BudgetService {
    private final BudgetMapper mapper;

    public BudgetService(BudgetMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 预算列表：year ≤ 0 时取当前年度；逐部门实时汇总占用额并补 committed/available/utilization 三项，
     * utilization 是保留 1 位小数的百分比字符串（额度为 0 时固定 "0.0"，避免除零）。
     * 每个部门单独查一次占用（部门数有限，属可接受的 N+1）。
     */
    public List<Map<String, Object>> list(int year) {
        int fiscalYear = year <= 0 ? LocalDate.now().getYear() : year;
        List<Map<String, Object>> rows = mapper.list(fiscalYear);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long departmentId = Long.valueOf(row.get("departmentId").toString());
            BigDecimal allocated = (BigDecimal) row.get("allocated");
            BigDecimal committed = committed(departmentId, fiscalYear);
            BigDecimal available = allocated.subtract(committed);
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("committed", committed.toPlainString());
            item.put("available", available.toPlainString());
            item.put("utilization", allocated.signum() <= 0 ? "0.0"
                    : committed.multiply(BigDecimal.valueOf(100)).divide(allocated, 1, java.math.RoundingMode.HALF_UP).toPlainString());
            result.add(item);
        }
        return result;
    }

    /**
     * 编制/调整部门年度预算：upsert 语义（同部门同年只保留一行），金额不得为负、部门必须存在。
     * 调整额度不会追溯已占用单据，是否超支仍由 check 在下次提交时判断。
     */
    @Transactional
    public void save(Long departmentId, int year, BigDecimal amount) {
        int fiscalYear = year <= 0 ? LocalDate.now().getYear() : year;
        if (departmentId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不能为空");
        if (amount == null || amount.signum() < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "预算金额不能为负数");
        if (mapper.countDepartment(departmentId) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不存在");
        Long user = SecurityUtils.currentUser().id();
        mapper.upsert(departmentId, fiscalYear, amount, user);
    }

    /**
     * 可用额度校验：部门/金额为空或非正数直接放行（不涉及预算）；该部门当年没编预算也放行（未编不拦）。
     * 只在提交/建单事务内调用，抛 409 并带可用额与本次需求额，方便前端直接展示差额。
     */
    public void check(Long departmentId, BigDecimal amount) {
        if (departmentId == null || amount == null || amount.signum() <= 0) return;
        int fiscalYear = LocalDate.now().getYear();
        BigDecimal allocated = mapper.findAllocated(departmentId, fiscalYear);
        if (allocated == null) return;
        BigDecimal available = allocated.subtract(committed(departmentId, fiscalYear));
        if (available.compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "部门年度预算不足：可用 " + available.toPlainString() + "，本次需要 " + amount.toPlainString());
        }
    }

    /** 占用额 = 申请口径 + 直采口径，两条 SQL 都可能返回 null（无记录），统一归零后再相加。 */
    private BigDecimal committed(Long departmentId, int fiscalYear) {
        BigDecimal requisitions = mapper.sumRequisitionCommitted(departmentId, fiscalYear);
        BigDecimal directOrders = mapper.sumDirectOrderCommitted(departmentId, fiscalYear);
        return (requisitions == null ? BigDecimal.ZERO : requisitions)
                .add(directOrders == null ? BigDecimal.ZERO : directOrders);
    }
}
