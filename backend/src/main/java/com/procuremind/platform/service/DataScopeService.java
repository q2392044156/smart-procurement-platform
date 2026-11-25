package com.procuremind.platform.service;

import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.DataScopeMapper;
import com.procuremind.platform.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.SqlFilter;

/**
 * 数据范围（行级权限）：把当前用户角色的数据范围翻译成 SQL 片段，或对单条记录做越权断言。
 *
 * <p>四档由窄到宽为 SELF、DEPARTMENT、DEPARTMENT_AND_CHILDREN、ALL，多角色并存时取最宽的一档；
 * 用户没有任何角色记录时按最窄的 SELF 处理。本类只回答「这条数据能不能看」，不判断审批人资格、
 * 申请人与审批人的关系，也不做接口级鉴权。返回的 {@link SqlFilter} 同时带子句与实参，实参全部
 * 由服务端生成（本人 id、部门 id、部门子树），用户输入一律走 #{} 绑定。</p>
 *
 * <p>依赖：DataScopeMapper（角色范围、部门树、范围内计数）、SecurityUtils（取当前登录人）。</p>
 */
@Service
public class DataScopeService {
    private final DataScopeMapper mapper;

    public DataScopeService(DataScopeMapper mapper) { this.mapper = mapper; }

    /** 采购申请的过滤条件：部门维度取 r.department_id，本人维度取 r.applicant_id。 */
    public SqlFilter requisitionFilter(String alias) {
        return filter(alias + ".department_id", alias + ".applicant_id");
    }

    /** 采购订单的过滤条件：部门取采购员档案（u）上的部门，本人维度取订单的 o.purchaser_id。 */
    public SqlFilter purchaseOrderFilter(String orderAlias, String purchaserAlias) {
        return filter(purchaserAlias + ".department_id", orderAlias + ".purchaser_id");
    }

    /** 采购申请的数据范围片断（内联版），供 mapper XML 直接拼接。 */
    public String requisitionScope() {
        return requisitionFilter("r").literal();
    }

    /** 采购订单的数据范围片断（内联版）。 */
    public String purchaseOrderScope(String orderAlias, String purchaserAlias) {
        return purchaseOrderFilter(orderAlias, purchaserAlias).literal();
    }

    /** 越权断言：按 id 数当前范围内可见的行，数不到统一 404，不区分「不存在」与「无权」。 */
    public void requireRequisition(Long id) {
        SqlFilter filter = requisitionFilter("r");
        require(mapper.countRequisitionInScope(id, filter.literal()));
    }

    /** 采购订单的越权断言，口径与 {@link #requireRequisition(Long)} 一致。 */
    public void requirePurchaseOrder(Long id) {
        SqlFilter filter = purchaseOrderFilter("o", "u");
        require(mapper.countPurchaseOrderInScope(id, filter.literal()));
    }

    /**
     * 流程业务键断言：只认 {@code REQUISITION:123} 与 {@code PURCHASE_ORDER:456} 两种前缀；
     * 格式错误与不支持的类型都按 400，避免把脏键当成越权而回 404。
     */
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

    /** 布尔版：把越权异常吞成 false，供任务列表那种「过滤而非报错」的场景使用。 */
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
        List<Long> departments = departmentSubtree(user.departmentId());
        String placeholders = String.join(",", Collections.nCopies(departments.size(), "?"));
        return new SqlFilter(departmentColumn + " in (" + placeholders + ")", departments.toArray());
    }

    /** 多角色取最宽：按 rank 取最大值，叠加管理员角色不会把范围收窄；没有角色记录时退回 SELF。 */
    private Scope effectiveScope(Long userId) {
        return mapper.roleScopes(userId).stream()
                .map(Scope::valueOf)
                .max(Comparator.comparingInt(Scope::rank))
                .orElse(Scope.SELF);
    }

    /** 部门及其全部下级部门，供数据范围与知识库可见性共用。 */
    public List<Long> departmentSubtree(Long root) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (Map<String, Object> row : mapper.departments()) {
            Long parent = row.get("parent_id") == null ? null : ((Number) row.get("parent_id")).longValue();
            if (parent != null) {
                Long id = ((Number) row.get("id")).longValue();
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
            }
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            if (result.add(id)) queue.addAll(children.getOrDefault(id, List.of()));
        }
        return new ArrayList<>(result);
    }

    private void require(int found) {
        if (found == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "数据不存在或不在当前数据范围内");
    }

    /** 四档由窄到宽，rank 只用于多角色取最宽，数值本身不落库。 */
    private enum Scope {
        SELF(0), DEPARTMENT(1), DEPARTMENT_AND_CHILDREN(2), ALL(3);
        private final int rank;
        Scope(int rank) { this.rank = rank; }
        int rank() { return rank; }
    }

    /**
     * 供 MyBatis ${} 使用的内联片断。占位符的实参全部是服务端生成的 Long 或枚举名，
     * 不含用户输入，因此内联不引入注入面；用户输入一律走 #{} 绑定。
     */
    
}
