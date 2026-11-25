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
 */
@Service
public class BudgetService {
    private final BudgetMapper mapper;

    public BudgetService(BudgetMapper mapper) {
        this.mapper = mapper;
    }

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

    @Transactional
    public void save(Long departmentId, int year, BigDecimal amount) {
        int fiscalYear = year <= 0 ? LocalDate.now().getYear() : year;
        if (departmentId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不能为空");
        if (amount == null || amount.signum() < 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "预算金额不能为负数");
        if (mapper.countDepartment(departmentId) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不存在");
        Long user = SecurityUtils.currentUser().id();
        mapper.upsert(departmentId, fiscalYear, amount, user);
    }

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

    private BigDecimal committed(Long departmentId, int fiscalYear) {
        BigDecimal requisitions = mapper.sumRequisitionCommitted(departmentId, fiscalYear);
        BigDecimal directOrders = mapper.sumDirectOrderCommitted(departmentId, fiscalYear);
        return (requisitions == null ? BigDecimal.ZERO : requisitions)
                .add(directOrders == null ? BigDecimal.ZERO : directOrders);
    }
}
