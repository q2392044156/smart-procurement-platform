package com.procuremind.platform.system;

import com.procuremind.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DataScopeService {
    private final JdbcTemplate jdbc;

    public DataScopeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SqlFilter requisitionFilter(String alias) {
        return filter(alias + ".department_id", alias + ".applicant_id");
    }

    public SqlFilter purchaseOrderFilter(String orderAlias, String purchaserAlias) {
        return filter(purchaserAlias + ".department_id", orderAlias + ".purchaser_id");
    }

    public void requireRequisition(Long id) {
        SqlFilter filter = requisitionFilter("r");
        require("select count(*) from pr_requisition r where r.id=? and r.deleted=0 and " + filter.clause(), id, filter.args());
    }

    public void requirePurchaseOrder(Long id) {
        SqlFilter filter = purchaseOrderFilter("o", "u");
        require("select count(*) from po_purchase_order o join sys_user u on u.id=o.purchaser_id where o.id=? and o.deleted=0 and " + filter.clause(), id, filter.args());
    }

    public void requireBusinessKey(String businessKey) {
        if (businessKey == null || !businessKey.contains(":")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "流程业务标识无效");
        }
        String[] parts = businessKey.split(":", 2);
        Long id;
        try { id = Long.valueOf(parts[1]); }
        catch (NumberFormatException e) { throw new BusinessException(HttpStatus.BAD_REQUEST, "流程业务标识无效"); }
        if ("REQUISITION".equals(parts[0])) requireRequisition(id);
        else if ("PURCHASE_ORDER".equals(parts[0])) requirePurchaseOrder(id);
        else throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的流程业务类型");
    }

    public boolean canAccessBusinessKey(String businessKey) {
        try { requireBusinessKey(businessKey); return true; }
        catch (BusinessException ignored) { return false; }
    }

    private SqlFilter filter(String departmentColumn, String ownerColumn) {
        CurrentUser user = SecurityUtils.currentUser();
        Scope scope = effectiveScope(user.id());
        if (scope == Scope.ALL) return new SqlFilter("1=1", new Object[0]);
        if (scope == Scope.SELF) return new SqlFilter(ownerColumn + "=?", new Object[]{user.id()});
        if (scope == Scope.DEPARTMENT) return new SqlFilter(departmentColumn + "=?", new Object[]{user.departmentId()});
        List<Long> departments = departmentAndChildren(user.departmentId());
        String placeholders = String.join(",", Collections.nCopies(departments.size(), "?"));
        return new SqlFilter(departmentColumn + " in (" + placeholders + ")", departments.toArray());
    }

    private Scope effectiveScope(Long userId) {
        return jdbc.queryForList("select r.data_scope from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=? and r.deleted=0", String.class, userId)
                .stream().map(Scope::valueOf).max(Comparator.comparingInt(Scope::rank)).orElse(Scope.SELF);
    }

    private List<Long> departmentAndChildren(Long root) {
        Map<Long, List<Long>> children = new HashMap<>();
        jdbc.query("select id,parent_id from sys_department where deleted=0", rs -> {
            Long parent = rs.getObject("parent_id", Long.class);
            if (parent != null) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(rs.getLong("id"));
        });
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            if (result.add(id)) queue.addAll(children.getOrDefault(id, List.of()));
        }
        return new ArrayList<>(result);
    }

    private void require(String sql, Long id, Object[] scopeArgs) {
        Object[] args = new Object[scopeArgs.length + 1];
        args[0] = id;
        System.arraycopy(scopeArgs, 0, args, 1, scopeArgs.length);
        Long count = jdbc.queryForObject(sql, Long.class, args);
        if (count == null || count == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "数据不存在或不在当前数据范围内");
    }

    private enum Scope {
        SELF(0), DEPARTMENT(1), DEPARTMENT_AND_CHILDREN(2), ALL(3);
        private final int rank;
        Scope(int rank) { this.rank = rank; }
        int rank() { return rank; }
    }

    public record SqlFilter(String clause, Object[] args) {}
}
