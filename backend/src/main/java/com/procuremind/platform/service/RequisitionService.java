package com.procuremind.platform.service;

import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.RequisitionMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.procuremind.platform.pojo.request.ReasonRequest;
import com.procuremind.platform.pojo.request.RequisitionItem;
import com.procuremind.platform.pojo.request.RequisitionRequest;

@Service
public class RequisitionService {
    private static final List<String> EDITABLE_STATUSES = List.of("DRAFT", "REJECTED");
    private static final List<String> CANCELLABLE_STATUSES = List.of("DRAFT", "REJECTED", "PENDING_APPROVAL", "APPROVED");
    private static final List<String> DELETABLE_STATUSES = List.of("DRAFT", "CANCELLED");

    private final RequisitionMapper mapper;
    private final WorkflowService workflow;
    private final DataScopeService dataScope;
    private final BusinessAttachmentService attachments;
    private final DocumentNumberService numbers;
    private final BudgetService budget;

    public RequisitionService(RequisitionMapper mapper, WorkflowService workflow, DataScopeService dataScope,
                              BusinessAttachmentService attachments, DocumentNumberService numbers, BudgetService budget) {
        this.mapper = mapper;
        this.workflow = workflow;
        this.dataScope = dataScope;
        this.attachments = attachments;
        this.numbers = numbers;
        this.budget = budget;
    }

    public List<Map<String, Object>> list() {
        return mapper.list(dataScope.requisitionScope());
    }

    public Map<String, Object> page(String keyword, String status, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        String scope = dataScope.requisitionScope();
        long total = mapper.countPage(scope, normalizedKeyword, normalizedStatus);
        List<Map<String, Object>> records =
                mapper.page(scope, normalizedKeyword, normalizedStatus, pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((total + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> detail(Long id) {
        dataScope.requireRequisition(id);
        Map<String, Object> data = mapper.findDetail(id);
        if (data == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购申请不存在");
        data.put("items", mapper.findItems(id));
        return data;
    }

    @Transactional
    public Long create(RequisitionRequest r) {
        CurrentUser user = SecurityUtils.currentUser();
        validate(r);
        String requisitionNo = numbers.next("PR");
        mapper.insertHeader(requisitionNo, user.departmentId(), user.id(), r.requiredDate(), r.purpose(),
                total(r.items()), user.id());
        Long id = mapper.findIdByNo(requisitionNo);
        insertItems(id, r.items(), user.id());
        return id;
    }

    @Transactional
    public void update(Long id, RequisitionRequest r) {
        Map<String, Object> row = lock(id);
        requireOwner(row, false);
        if (!EDITABLE_STATUSES.contains(row.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能编辑");
        }
        validate(r);
        Long user = SecurityUtils.currentUser().id();
        mapper.updateHeader(id, r.requiredDate(), r.purpose(), total(r.items()), user);
        mapper.softDeleteItems(id, user);
        insertItems(id, r.items(), user);
    }

    @Transactional
    public void submit(Long id) {
        Map<String, Object> row = lock(id);
        requireOwner(row, false);
        if (!EDITABLE_STATUSES.contains(row.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能提交");
        }
        budget.check(((Number) row.get("department_id")).longValue(), (BigDecimal) row.get("budget_amount"));
        String processId = workflow.startRequisition(id, (BigDecimal) row.get("budget_amount"),
                SecurityUtils.currentUser());
        String nextStatus = workflow.isFinished(processId) ? "APPROVED" : "PENDING_APPROVAL";
        mapper.updateStatus(id, nextStatus, processId, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void cancel(Long id, ReasonRequest r) {
        Map<String, Object> row = lock(id);
        requireOwner(row, true);
        String status = row.get("status").toString();
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能取消");
        }
        if ("PENDING_APPROVAL".equals(status)) {
            workflow.cancelActiveProcesses("REQUISITION:" + id, r.reason());
        }
        mapper.cancel(id, r.reason().trim(), SecurityUtils.currentUser().id());
    }

    @Transactional
    public void delete(Long id) {
        Map<String, Object> row = lock(id);
        requireOwner(row, true);
        if (!DELETABLE_STATUSES.contains(row.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有草稿或已取消申请可以删除");
        }
        Long user = SecurityUtils.currentUser().id();
        mapper.softDeleteItems(id, user);
        mapper.softDelete(id, user);
        attachments.unlinkBusiness("REQUISITION", id);
    }

    private Map<String, Object> lock(Long id) {
        Map<String, Object> row = mapper.lockById(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购申请不存在");
        return row;
    }

    private void requireOwner(Map<String, Object> row, boolean adminAllowed) {
        CurrentUser user = SecurityUtils.currentUser();
        if (!row.get("applicant_id").toString().equals(user.id().toString())
                && !(adminAllowed && user.roles().contains("ADMIN"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能操作自己的申请");
        }
    }

    private void insertItems(Long id, List<RequisitionItem> items, Long user) {
        for (RequisitionItem item : items) {
            mapper.insertItem(id, item.materialId(), item.quantity(), item.estimatedUnitPrice(), item.remark(), user);
        }
    }

    private void validate(RequisitionRequest r) {
        if (r.requiredDate().isBefore(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "需求日期不能早于今天");
        }
        if (r.items() == null || r.items().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "至少添加一条采购明细");
        }
        Set<Long> materialIds = new HashSet<>();
        for (RequisitionItem item : r.items()) {
            if (item.quantity() == null || item.quantity().signum() <= 0
                    || item.estimatedUnitPrice() == null || item.estimatedUnitPrice().signum() < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "数量必须大于0且价格不能为负数");
            }
            if (!materialIds.add(item.materialId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "同一物资不能重复添加");
            }
            if (mapper.countEnabledMaterial(item.materialId()) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "采购物资不存在或已停用");
            }
        }
    }

    private BigDecimal total(List<RequisitionItem> items) {
        return items.stream()
                .map(item -> item.quantity().multiply(item.estimatedUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    

    

    
}
