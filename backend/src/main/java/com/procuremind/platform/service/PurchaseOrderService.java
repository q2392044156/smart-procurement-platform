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
import com.procuremind.platform.pojo.request.PurchaseOrderDirectItem;
import com.procuremind.platform.pojo.request.PurchaseOrderDirectRequest;
import com.procuremind.platform.pojo.request.PurchaseOrderRequest;
import com.procuremind.platform.pojo.request.PurchaseOrderUpdateItem;
import com.procuremind.platform.pojo.request.PurchaseOrderUpdateRequest;
import com.procuremind.platform.pojo.request.ReasonRequest;
import com.procuremind.platform.pojo.row.PurchaseOrderLock;
import com.procuremind.platform.pojo.row.RequisitionStatus;

/**
 * 采购订单：由已审批申请转换或直采创建，支持改价、提交审批、取消、关闭与软删。
 *
 * <p>状态机：DRAFT/REJECTED →（提交）PENDING_APPROVAL →（审批）APPROVED →（收货）PARTIALLY_RECEIVED → RECEIVED
 * →（关闭）CLOSED；任意可取消态可落 CANCELLED。写操作统一先加锁读再判状态，避免并发改同一单。
 *
 * <p>边界：不做出入库与批次追踪（ReceiptService）、不预占预算（只调 BudgetService 校验额度）、不物理删数据、
 * 不维护附件文件本身（只解绑关联）。依赖 PurchaseOrderMapper、WorkflowService（审批流程）、DataScopeService
 * （数据范围）、DocumentNumberService（PO 单号）、BusinessAttachmentService、BudgetService。</p>
 */
@Service
public class PurchaseOrderService {
    /** 可编辑/可提交：只有草稿与驳回态能改单，一旦提交内容即锁定。 */
    private static final List<String> EDITABLE_STATUSES = List.of("DRAFT", "REJECTED");
    /** 可取消：已审批但尚无收货记录的订单也能取消，取消会同时终止在途审批流程。 */
    private static final List<String> CANCELLABLE_STATUSES = List.of("DRAFT", "REJECTED", "PENDING_APPROVAL", "APPROVED");
    /** 可删除：仅草稿、驳回、已取消；走软删，单号与审计痕迹保留。 */
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

    /** 全量列表（不分页），只按数据范围过滤，供导出与下拉选择使用；大结果集请走 page()。 */
    public List<Map<String, Object>> list() {
        return mapper.list(dataScope.purchaseOrderScope("o", "u"));
    }

    /**
     * 分页查询：页码最小 1、页大小夹在 1~100，关键字去空白、状态转大写，空值即“不过滤”。
     * total 由 mapper 以 long 返回，这里转 int 并在 Java 侧算总页数，避免再查一次 count。
     */
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

    /** 详情：先做数据范围校验（越权直接 403），主表与明细分两次查后拼装，明细不参与分页。 */
    public Map<String, Object> detail(Long id) {
        dataScope.requirePurchaseOrder(id);
        Map<String, Object> data = mapper.findDetail(id);
        if (data == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购订单不存在");
        data.put("items", mapper.findItems(id));
        return data;
    }

    /**
     * 申请转订单：加锁读申请行防止同一申请被并发转成两张单；只接受 APPROVED 申请，供应商必须处于合格状态。
     * 价格覆盖只允许出现在申请明细内且非负；总价按“数量 × 单价”逐行累加。单号由序列生成、事务回滚也不回收。
     * insertHeader 不回填主键，故按单号反查订单 id 再插明细；最后把申请标记为已转换。
     */
    @Transactional
    public Long convert(Long requisitionId, PurchaseOrderRequest r) {
        dataScope.requireRequisition(requisitionId);
        RequisitionStatus req = mapper.lockRequisition(requisitionId);
        if (req == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购申请不存在");
        if (!"APPROVED".equals(req.status())) throw new BusinessException(HttpStatus.CONFLICT, "只有已审批申请可以转换为订单");
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

    /** 取价优先级：本次覆盖价 > 申请单估算单价；申请明细是 Map，故键取字符串形式的物资 id。 */
    private BigDecimal priceOf(Map<String, Object> item, Map<String, BigDecimal> overrides) {
        String material = item.get("material_id").toString();
        if (overrides != null && overrides.containsKey(material)) return overrides.get(material);
        return (BigDecimal) item.get("estimated_unit_price");
    }

    /**
     * 改单：只允许改单价、税率、交期与备注，数量不可变——数量变更须取消后重新转换，以免与已收数量对不上。
     * 必须一次性提交全部明细（多一条、少一条、重复 id 都拒绝），总价按原数量 × 新单价重算。
     */
    @Transactional
    public void update(Long id, PurchaseOrderUpdateRequest r) {
        PurchaseOrderLock order = orderForUpdate(id);
        requireOwner(order, true);
        if (!EDITABLE_STATUSES.contains(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能编辑");
        }
        if (r.deliveryDate().isBefore(LocalDate.now())) throw new BusinessException(HttpStatus.BAD_REQUEST, "交付日期不能早于今天");
        requireQualifiedSupplier(r.supplierId());
        List<Map<String, Object>> existing = mapper.findItemQuantities(id);
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (var item : existing) byId.put(((Number) item.get("id")).longValue(), item);
        Set<Long> supplied = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderUpdateItem item : r.items()) {
            if (!supplied.add(item.id()) || !byId.containsKey(item.id())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "订单明细不匹配");
            }
            total = total.add(((BigDecimal) byId.get(item.id()).get("quantity")).multiply(item.unitPrice()));
        }
        if (supplied.size() != byId.size()) throw new BusinessException(HttpStatus.BAD_REQUEST, "必须提交全部订单明细");
        Long user = SecurityUtils.currentUser().id();
        mapper.updateHeader(id, r.supplierId(), r.deliveryDate(), r.taxRate(), total, r.terms(), user);
        for (PurchaseOrderUpdateItem item : r.items()) {
            mapper.updateItemPrice(item.id(), id, item.unitPrice(), r.taxRate(), item.remark(), user);
        }
    }

    /**
     * 提交审批：只有创建人可提交；启动流程后若实例已结束（申请人兼任全部审批角色被自动跳过），
     * 直接落 APPROVED，否则 PENDING_APPROVAL。流程实例 id 与状态同一事务回写。
     */
    @Transactional
    public void submit(Long id) {
        PurchaseOrderLock row = mapper.lockById(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购订单不存在");
        if (!row.purchaserId().equals(SecurityUtils.currentUser().id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能提交自己创建的订单");
        }
        if (!EDITABLE_STATUSES.contains(row.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能提交审批");
        }
        String process = workflow.startPurchaseOrder(id, row.totalAmount(), SecurityUtils.currentUser());
        String nextStatus = workflow.isFinished(process) ? "APPROVED" : "PENDING_APPROVAL";
        mapper.updateStatus(id, nextStatus, process, SecurityUtils.currentUser().id());
    }

    /**
     * 取消：已审批单必须无收货记录才可取消；审批中的单先删除流程实例再改状态，防止流程继续流转。
     * 取消后回退来源申请，使其可以重新转换。
     */
    @Transactional
    public void cancel(Long id, ReasonRequest r) {
        PurchaseOrderLock order = orderForUpdate(id);
        requireOwner(order, true);
        String status = order.status();
        if (!CANCELLABLE_STATUSES.contains(status)) throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能取消");
        if ("APPROVED".equals(status) && receiptCount(id) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "已有收货记录的订单不能取消");
        }
        if ("PENDING_APPROVAL".equals(status)) workflow.cancelActiveProcesses("PURCHASE_ORDER:" + id, r.reason());
        Long user = SecurityUtils.currentUser().id();
        mapper.cancel(id, r.reason().trim(), user);
        restoreSource(order.requisitionId(), user);
    }

    /** 关闭：仅“已全部收货”的订单可关闭，语义是归档结项，关闭后不再产生收货或变更。 */
    @Transactional
    public void close(Long id) {
        PurchaseOrderLock order = orderForUpdate(id);
        requireOwner(order, true);
        if (!"RECEIVED".equals(order.status())) throw new BusinessException(HttpStatus.CONFLICT, "只有已全部收货订单可以关闭");
        mapper.closeOrder(id, SecurityUtils.currentUser().id());
    }

    /**
     * 删除：软删明细与主表（保留单号与审计），解绑附件关联（不删文件本体），并回退来源申请。
     * 有收货记录的订单不允许删除，否则库存流水会指向一张不存在的订单。
     */
    @Transactional
    public void delete(Long id) {
        PurchaseOrderLock order = orderForUpdate(id);
        requireOwner(order, true);
        if (!DELETABLE_STATUSES.contains(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有草稿、驳回或已取消订单可以删除");
        }
        if (receiptCount(id) > 0) throw new BusinessException(HttpStatus.CONFLICT, "已有收货记录的订单不能删除");
        Long user = SecurityUtils.currentUser().id();
        mapper.softDeleteItems(id, user);
        mapper.softDelete(id, user);
        attachments.unlinkBusiness("PURCHASE_ORDER", id);
        restoreSource(order.requisitionId(), user);
    }

    /** 加锁读（SELECT ... FOR UPDATE）；查不到即 404，后续状态判断都可安全基于这一行快照。 */
    private PurchaseOrderLock orderForUpdate(Long id) {
        PurchaseOrderLock row = mapper.lockById(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购订单不存在");
        return row;
    }

    /** 属主校验：默认只允许创建人；adminAllowed 为 true 时 ADMIN 也能操作（提交除外，提交永远是本人行为）。 */
    private void requireOwner(PurchaseOrderLock order, boolean adminAllowed) {
        var user = SecurityUtils.currentUser();
        if (!order.purchaserId().equals(user.id())
                && !(adminAllowed && user.roles().contains("ADMIN"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能操作自己创建的订单");
        }
    }

    /** 收货笔数：取消与删除两处都要用，抽出来保证口径一致。 */
    private long receiptCount(Long id) {
        return mapper.countReceipts(id);
    }

    // ==================== 直采订单（供 Agent / 内部接口使用） ====================

    /**
     * 直采创建（供 Agent / 内部接口）：缺省值兜底——供应商取系统默认合格供应商、交期 +7 天、税率 0.13。
     * 先算总价再调 BudgetService 校验额度（只校验不预占），落库后直接返回 DRAFT 摘要，不走审批。
     */
    @Transactional
    public Map<String, Object> createDirect(PurchaseOrderDirectRequest request) {
        var currentUser = SecurityUtils.currentUser();
        Long user = currentUser.id();
        Long supplierId = request.supplierId() == null ? defaultSupplier() : request.supplierId();
        requireQualifiedSupplier(supplierId);
        List<PurchaseOrderDirectItem> items = requireDirectItems(request.items());
        LocalDate delivery = request.deliveryDate() == null ? LocalDate.now().plusDays(7) : request.deliveryDate();
        if (delivery.isBefore(LocalDate.now())) throw badRequest("交付日期不能早于今天");
        BigDecimal taxRate = request.taxRate() == null ? new BigDecimal("0.13") : request.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw badRequest("税率必须在 0~1 之间");
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderDirectItem item : items) total = total.add(item.quantity().multiply(item.unitPrice()));
        budget.check(currentUser.departmentId(), total);
        String no = numbers.next("PO");
        mapper.insertHeader(no, null, supplierId, user, delivery, taxRate, total, blank(request.terms()), user);
        Long id = mapper.findIdByNo(no);
        for (PurchaseOrderDirectItem item : items) {
            mapper.insertItem(id, item.partId(), item.quantity(), item.unitPrice(), taxRate, blank(item.remark()), user);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(id));
        result.put("orderNo", no);
        result.put("status", "DRAFT");
        result.put("totalAmount", total.toPlainString());
        return result;
    }

    /**
     * 直采改单：与 convert 来源的订单不同，这里允许整体替换明细（软删后重插）。
     * 前提是直采订单尚无可引用明细的收货记录，状态闸门已限定在 DRAFT/REJECTED。
     */
    @Transactional
    public void updateDirect(Long id, PurchaseOrderDirectRequest request) {
        PurchaseOrderLock order = orderForUpdate(id);
        requireOwner(order, true);
        if (!EDITABLE_STATUSES.contains(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能编辑");
        }
        Long user = SecurityUtils.currentUser().id();
        Long supplierId = request.supplierId() == null ? order.supplierId() : request.supplierId();
        requireQualifiedSupplier(supplierId);
        List<PurchaseOrderDirectItem> items = requireDirectItems(request.items());
        LocalDate delivery = request.deliveryDate() == null ? LocalDate.now().plusDays(7) : request.deliveryDate();
        if (delivery.isBefore(LocalDate.now())) throw badRequest("交付日期不能早于今天");
        BigDecimal taxRate = request.taxRate() == null ? new BigDecimal("0.13") : request.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw badRequest("税率必须在 0~1 之间");
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderDirectItem item : items) total = total.add(item.quantity().multiply(item.unitPrice()));
        mapper.softDeleteItems(id, user);
        for (PurchaseOrderDirectItem item : items) {
            mapper.insertItem(id, item.partId(), item.quantity(), item.unitPrice(), taxRate, blank(item.remark()), user);
        }
        mapper.updateDirectHeader(id, supplierId, delivery, taxRate, total, blank(request.terms()), user);
    }

    /** 默认供应商：取系统配置的合格供应商，没有则说明主数据没维护好，直接 400 而不是静默插一条空单。 */
    private Long defaultSupplier() {
        Long id = mapper.findDefaultSupplierId();
        if (id == null) throw badRequest("没有可用的合格供应商");
        return id;
    }

    /** 供应商准入：只认“合格”状态，冻结/待审核的供应商不能下单；数量为 0 即不合格。 */
    private void requireQualifiedSupplier(Long supplierId) {
        if (mapper.countQualifiedSupplier(supplierId) == 0) throw badRequest("供应商未处于合格状态");
    }

    /** 直采明细校验清单：非空、物资必填、同物资不可重复、数量 > 0、单价 ≥ 0、物资存在且启用。 */
    private List<PurchaseOrderDirectItem> requireDirectItems(List<PurchaseOrderDirectItem> items) {
        if (items == null || items.isEmpty()) throw badRequest("订单明细不能为空");
        Set<Long> seen = new HashSet<>();
        for (PurchaseOrderDirectItem item : items) {
            if (item == null || item.partId() == null) throw badRequest("订单明细缺少物资");
            if (!seen.add(item.partId())) throw badRequest("同一物资不能重复添加");
            if (item.quantity() == null || item.quantity().signum() <= 0) throw badRequest("数量必须大于 0");
            if (item.unitPrice() == null || item.unitPrice().signum() < 0) throw badRequest("单价不能为负数");
            if (mapper.countEnabledMaterial(item.partId()) == 0) throw badRequest("物资不存在或已停用：" + item.partId());
        }
        return items;
    }

    /** 直采校验统一抛 400：这些是请求参数问题，与状态冲突（409）区分开。 */
    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    /** 空白字符串归一化为 null，避免库里同时存在 "" 与 NULL 两种“没填”。 */
    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 回退来源申请：只有该申请名下已无其它活动订单时才恢复为“可转换”，
     * 否则一张申请转多单的场景下，删掉其中一单会把整张申请错误地放回可转换池。
     * requisitionId 是 Object（mapper 返回类型不固定），故先做 Number 归一化再判空。
     */
    private void restoreSource(Object requisitionId, Long user) {
        if (requisitionId == null) return;
        long id = ((Number) requisitionId).longValue();
        if (mapper.countActiveOrdersByRequisition(id) == 0) mapper.restoreRequisition(id, user);
    }

    /** 物资历史成交价：按当前用户的数据范围返回最近 50 条订单明细，供比价参考，不做聚合。 */
    public List<Map<String, Object>> priceHistory(Long materialId) {
        return mapper.priceHistory(materialId, dataScope.purchaseOrderScope("o", "u"));
    }

    
    
    
    
    
    
}
