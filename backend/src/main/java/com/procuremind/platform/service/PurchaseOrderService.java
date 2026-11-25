package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.PurchaseOrderMapper;
import com.procuremind.platform.service.BusinessAttachmentService;
import com.procuremind.platform.utils.SecurityUtils;
import com.procuremind.platform.service.DataScopeService;
import com.procuremind.platform.service.WorkflowService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class PurchaseOrderService {
    private static final List<String> EDITABLE_STATUSES = List.of("DRAFT", "REJECTED");
    private static final List<String> CANCELLABLE_STATUSES = List.of("DRAFT", "REJECTED", "PENDING_APPROVAL", "APPROVED");
    private static final List<String> DELETABLE_STATUSES = List.of("DRAFT", "REJECTED", "CANCELLED");

    private final PurchaseOrderMapper mapper;
    private final WorkflowService workflow;
    private final DataScopeService dataScope;
    private final BusinessAttachmentService attachments;
    private final com.procuremind.platform.service.DocumentNumberService numbers;
    private final com.procuremind.platform.service.BudgetService budget;

    public PurchaseOrderService(PurchaseOrderMapper mapper, WorkflowService workflow, DataScopeService dataScope,
                                BusinessAttachmentService attachments,
                                com.procuremind.platform.service.DocumentNumberService numbers,
                                com.procuremind.platform.service.BudgetService budget) {
        this.mapper = mapper;
        this.workflow = workflow;
        this.dataScope = dataScope;
        this.attachments = attachments;
        this.numbers = numbers;
        this.budget = budget;
    }

    public List<Map<String, Object>> list() {
        return mapper.list(dataScope.purchaseOrderScope("o", "u"));
    }

    public Map<String, Object> page(String keyword, String status, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String state = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String scope = dataScope.purchaseOrderScope("o", "u");
        long totalCount = mapper.countPage(scope, like, state);
        List<Map<String, Object>> records = mapper.page(scope, like, state, pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) totalCount);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((totalCount + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> detail(Long id) {
        dataScope.requirePurchaseOrder(id);
        Map<String, Object> data = mapper.findDetail(id);
        if (data == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购订单不存在");
        data.put("items", mapper.findItems(id));
        return data;
    }

    @Transactional
    public Long convert(Long requisitionId, Request r) {
        dataScope.requireRequisition(requisitionId);
        Map<String, Object> req = mapper.lockRequisition(requisitionId);
        if (req == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购申请不存在");
        if (!"APPROVED".equals(req.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有已审批申请可以转换为订单");
        requireQualifiedSupplier(r.supplierId());
        var items = mapper.findRequisitionItems(requisitionId);
        if (items.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "申请单没有可转换的明细");
        Set<String> allowed = new HashSet<>();
        for (var item : items) allowed.add(item.get("material_id").toString());
        if (r.priceOverrides() != null) {
            for (var entry : r.priceOverrides().entrySet()) {
                if (!allowed.contains(entry.getKey()) || entry.getValue() == null || entry.getValue().signum() < 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "订单价格覆盖项无效");
                }
            }
        }
        String no = numbers.next("PO");
        BigDecimal total = BigDecimal.ZERO;
        for (var i : items) {
            total = total.add(((BigDecimal) i.get("quantity")).multiply(priceOf(i, r.priceOverrides())));
        }
        Long user = SecurityUtils.currentUser().id();
        mapper.insertHeader(no, requisitionId, r.supplierId(), user, r.deliveryDate(), r.taxRate(), total, r.terms(), user);
        Long id = mapper.findIdByNo(no);
        for (var i : items) {
            Long materialId = Long.valueOf(i.get("material_id").toString());
            mapper.insertItem(id, materialId, (BigDecimal) i.get("quantity"), priceOf(i, r.priceOverrides()),
                    r.taxRate(), null, user);
        }
        mapper.markRequisitionConverted(requisitionId, user);
        return id;
    }

    private BigDecimal priceOf(Map<String, Object> item, Map<String, BigDecimal> overrides) {
        String material = item.get("material_id").toString();
        if (overrides != null && overrides.containsKey(material)) return overrides.get(material);
        return (BigDecimal) item.get("estimated_unit_price");
    }

    @Transactional
    public void update(Long id, UpdateRequest r) {
        Map<String, Object> order = orderForUpdate(id);
        requireOwner(order, true);
        if (!EDITABLE_STATUSES.contains(order.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能编辑");
        }
        if (r.deliveryDate().isBefore(LocalDate.now())) throw new BusinessException(HttpStatus.BAD_REQUEST, "交付日期不能早于今天");
        requireQualifiedSupplier(r.supplierId());
        List<Map<String, Object>> existing = mapper.findItemQuantities(id);
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (var item : existing) byId.put(((Number) item.get("id")).longValue(), item);
        Set<Long> supplied = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (UpdateItem item : r.items()) {
            if (!supplied.add(item.id()) || !byId.containsKey(item.id())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "订单明细不匹配");
            }
            total = total.add(((BigDecimal) byId.get(item.id()).get("quantity")).multiply(item.unitPrice()));
        }
        if (supplied.size() != byId.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, "必须提交全部订单明细");
        Long user = SecurityUtils.currentUser().id();
        mapper.updateHeader(id, r.supplierId(), r.deliveryDate(), r.taxRate(), total, r.terms(), user);
        for (UpdateItem item : r.items()) {
            mapper.updateItemPrice(item.id(), id, item.unitPrice(), r.taxRate(), item.remark(), user);
        }
    }

    @Transactional
    public void submit(Long id) {
        Map<String, Object> row = mapper.lockById(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购订单不存在");
        if (!row.get("purchaser_id").toString().equals(SecurityUtils.currentUser().id().toString())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能提交自己创建的订单");
        }
        if (!EDITABLE_STATUSES.contains(row.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能提交审批");
        }
        String process = workflow.startPurchaseOrder(id, (BigDecimal) row.get("total_amount"), SecurityUtils.currentUser());
        String nextStatus = workflow.isFinished(process) ? "APPROVED" : "PENDING_APPROVAL";
        mapper.updateStatus(id, nextStatus, process, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void cancel(Long id, ReasonRequest r) {
        Map<String, Object> order = orderForUpdate(id);
        requireOwner(order, true);
        String status = order.get("status").toString();
        if (!CANCELLABLE_STATUSES.contains(status)) throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能取消");
        if ("APPROVED".equals(status) && receiptCount(id) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "已有收货记录的订单不能取消");
        }
        if ("PENDING_APPROVAL".equals(status)) workflow.cancelActiveProcesses("PURCHASE_ORDER:" + id, r.reason());
        Long user = SecurityUtils.currentUser().id();
        mapper.cancel(id, r.reason().trim(), user);
        restoreSource(order.get("requisition_id"), user);
    }

    @Transactional
    public void close(Long id) {
        Map<String, Object> order = orderForUpdate(id);
        requireOwner(order, true);
        if (!"RECEIVED".equals(order.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有已全部收货订单可以关闭");
        mapper.closeOrder(id, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void delete(Long id) {
        Map<String, Object> order = orderForUpdate(id);
        requireOwner(order, true);
        if (!DELETABLE_STATUSES.contains(order.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有草稿、驳回或已取消订单可以删除");
        }
        if (receiptCount(id) > 0) throw new BusinessException(HttpStatus.CONFLICT, "已有收货记录的订单不能删除");
        Long user = SecurityUtils.currentUser().id();
        mapper.softDeleteItems(id, user);
        mapper.softDelete(id, user);
        attachments.unlinkBusiness("PURCHASE_ORDER", id);
        restoreSource(order.get("requisition_id"), user);
    }

    private Map<String, Object> orderForUpdate(Long id) {
        Map<String, Object> row = mapper.lockById(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购订单不存在");
        return row;
    }

    private void requireOwner(Map<String, Object> order, boolean adminAllowed) {
        var user = SecurityUtils.currentUser();
        if (!order.get("purchaser_id").toString().equals(user.id().toString())
                && !(adminAllowed && user.roles().contains("ADMIN"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能操作自己创建的订单");
        }
    }

    private long receiptCount(Long id) {
        return mapper.countReceipts(id);
    }

    // ==================== 直采订单（供 Agent / 内部接口使用） ====================

    @Transactional
    public Map<String, Object> createDirect(DirectOrderRequest request) {
        var currentUser = SecurityUtils.currentUser();
        Long user = currentUser.id();
        Long supplierId = request.supplierId() == null ? defaultSupplier() : request.supplierId();
        requireQualifiedSupplier(supplierId);
        List<DirectItem> items = requireDirectItems(request.items());
        LocalDate delivery = request.deliveryDate() == null ? LocalDate.now().plusDays(7) : request.deliveryDate();
        if (delivery.isBefore(LocalDate.now())) throw badRequest("交付日期不能早于今天");
        BigDecimal taxRate = request.taxRate() == null ? new BigDecimal("0.13") : request.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw badRequest("税率必须在 0~1 之间");
        BigDecimal total = BigDecimal.ZERO;
        for (DirectItem item : items) total = total.add(item.quantity().multiply(item.unitPrice()));
        budget.check(currentUser.departmentId(), total);
        String no = numbers.next("PO");
        mapper.insertHeader(no, null, supplierId, user, delivery, taxRate, total, blank(request.terms()), user);
        Long id = mapper.findIdByNo(no);
        for (DirectItem item : items) {
            mapper.insertItem(id, item.partId(), item.quantity(), item.unitPrice(), taxRate, blank(item.remark()), user);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(id));
        result.put("orderNo", no);
        result.put("status", "DRAFT");
        result.put("totalAmount", total.toPlainString());
        return result;
    }

    @Transactional
    public void updateDirect(Long id, DirectOrderRequest request) {
        Map<String, Object> order = orderForUpdate(id);
        requireOwner(order, true);
        if (!EDITABLE_STATUSES.contains(order.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能编辑");
        }
        Long user = SecurityUtils.currentUser().id();
        Long supplierId = request.supplierId() == null ? ((Number) order.get("supplier_id")).longValue() : request.supplierId();
        requireQualifiedSupplier(supplierId);
        List<DirectItem> items = requireDirectItems(request.items());
        LocalDate delivery = request.deliveryDate() == null ? LocalDate.now().plusDays(7) : request.deliveryDate();
        if (delivery.isBefore(LocalDate.now())) throw badRequest("交付日期不能早于今天");
        BigDecimal taxRate = request.taxRate() == null ? new BigDecimal("0.13") : request.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw badRequest("税率必须在 0~1 之间");
        BigDecimal total = BigDecimal.ZERO;
        for (DirectItem item : items) total = total.add(item.quantity().multiply(item.unitPrice()));
        mapper.softDeleteItems(id, user);
        for (DirectItem item : items) {
            mapper.insertItem(id, item.partId(), item.quantity(), item.unitPrice(), taxRate, blank(item.remark()), user);
        }
        mapper.updateDirectHeader(id, supplierId, delivery, taxRate, total, blank(request.terms()), user);
    }

    private Long defaultSupplier() {
        Long id = mapper.findDefaultSupplierId();
        if (id == null) throw badRequest("没有可用的合格供应商");
        return id;
    }

    private void requireQualifiedSupplier(Long supplierId) {
        if (mapper.countQualifiedSupplier(supplierId) == 0) throw badRequest("供应商未处于合格状态");
    }

    private List<DirectItem> requireDirectItems(List<DirectItem> items) {
        if (items == null || items.isEmpty()) throw badRequest("订单明细不能为空");
        Set<Long> seen = new HashSet<>();
        for (DirectItem item : items) {
            if (item == null || item.partId() == null) throw badRequest("订单明细缺少物资");
            if (!seen.add(item.partId())) throw badRequest("同一物资不能重复添加");
            if (item.quantity() == null || item.quantity().signum() <= 0) throw badRequest("数量必须大于 0");
            if (item.unitPrice() == null || item.unitPrice().signum() < 0) throw badRequest("单价不能为负数");
            if (mapper.countEnabledMaterial(item.partId()) == 0) throw badRequest("物资不存在或已停用：" + item.partId());
        }
        return items;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void restoreSource(Object requisitionId, Long user) {
        if (requisitionId == null) return;
        long id = ((Number) requisitionId).longValue();
        if (mapper.countActiveOrdersByRequisition(id) == 0) mapper.restoreRequisition(id, user);
    }

    /** 物资历史成交价：按当前用户的数据范围返回最近 50 条订单明细。 */
    public List<Map<String, Object>> priceHistory(Long materialId) {
        return mapper.priceHistory(materialId, dataScope.purchaseOrderScope("o", "u"));
    }

    public record Request(@NotNull Long supplierId, @NotNull @FutureOrPresent LocalDate deliveryDate, @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate, @Size(max=1000) String terms, Map<String,BigDecimal> priceOverrides) {}
    public record UpdateRequest(@NotNull Long supplierId, @NotNull @FutureOrPresent LocalDate deliveryDate, @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate, @Size(max=1000) String terms, @NotEmpty @Size(max=100) List<@Valid UpdateItem> items) {}
    public record UpdateItem(@NotNull Long id, @NotNull @DecimalMin("0") BigDecimal unitPrice, @Size(max=500) String remark) {}
    public record ReasonRequest(@NotBlank @Size(max=500) String reason) {}
    public record DirectOrderRequest(Long supplierId, @FutureOrPresent LocalDate deliveryDate, @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate, @Size(max=1000) String terms, @NotEmpty @Size(max=100) List<@Valid DirectItem> items) {}
    public record DirectItem(@NotNull Long partId, @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity, @NotNull @DecimalMin("0") BigDecimal unitPrice, @Size(max=500) String remark) {}
}
