package com.procuremind.platform.budget;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    public BudgetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(int year) {
        int fiscalYear = year <= 0 ? LocalDate.now().getYear() : year;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(b.id as char) id,cast(b.department_id as char) departmentId,d.code departmentCode,d.name department,
                       b.fiscal_year fiscalYear,b.amount allocated
                from fin_budget b join sys_department d on d.id=b.department_id
                where b.fiscal_year=? order by d.sort_order,d.id
                """, fiscalYear);
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
        Long exists = jdbc.queryForObject("select count(*) from sys_department where id=? and deleted=0", Long.class, departmentId);
        if (exists == null || exists == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "部门不存在");
        Long user = SecurityUtils.currentUser().id();
        jdbc.update("""
                insert into fin_budget(department_id,fiscal_year,amount,created_by,updated_by)
                values(?,?,?,?,?) on duplicate key update amount=values(amount),updated_by=values(updated_by)
                """, departmentId, fiscalYear, amount, user, user);
    }

    public void check(Long departmentId, BigDecimal amount) {
        if (departmentId == null || amount == null || amount.signum() <= 0) return;
        int fiscalYear = LocalDate.now().getYear();
        List<BigDecimal> allocatedRows = jdbc.queryForList(
                "select amount from fin_budget where department_id=? and fiscal_year=?", BigDecimal.class, departmentId, fiscalYear);
        if (allocatedRows.isEmpty()) return;
        BigDecimal allocated = allocatedRows.getFirst();
        BigDecimal available = allocated.subtract(committed(departmentId, fiscalYear));
        if (available.compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "部门年度预算不足：可用 " + available.toPlainString() + "，本次需要 " + amount.toPlainString());
        }
    }

    private BigDecimal committed(Long departmentId, int fiscalYear) {
        BigDecimal requisitions = jdbc.queryForObject("""
                select coalesce(sum(budget_amount),0) from pr_requisition
                where department_id=? and deleted=0 and year(created_at)=?
                  and status in ('PENDING_APPROVAL','APPROVED','CONVERTED')
                """, BigDecimal.class, departmentId, fiscalYear);
        BigDecimal directOrders = jdbc.queryForObject("""
                select coalesce(sum(o.total_amount),0) from po_purchase_order o
                join sys_user u on u.id=o.purchaser_id
                where o.requisition_id is null and o.deleted=0 and u.department_id=? and year(o.created_at)=?
                  and o.status in ('PENDING_APPROVAL','APPROVED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
                """, BigDecimal.class, departmentId, fiscalYear);
        return (requisitions == null ? BigDecimal.ZERO : requisitions)
                .add(directOrders == null ? BigDecimal.ZERO : directOrders);
    }
}
