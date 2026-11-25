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
import com.procuremind.platform.pojo.row.RequisitionLock;
import com.procuremind.platform.pojo.request.RequisitionRequest;

/**
 * 采购申请域的应用服务：草稿建单、编辑、提交审批、取消、软删，以及列表/分页/详情查询。
 *
 * <p>状态机：DRAFT / REJECTED →（提交）PENDING_APPROVAL →（审批）APPROVED 或 REJECTED；
 * 取消只落 CANCELLED，删除只做软删。写操作统一 {@code @Transactional}，先加锁读再判状态与归属，
 * 状态校验通过后才允许写，避免并发提交/取消互相覆盖。
 *
 * <p>依赖：RequisitionMapper（持久化与行锁）、WorkflowService（Flowable 流程启动与终止）、
 * BudgetService（提交前预算校验）、DocumentNumberService（PR 单号）、DataScopeService（数据范围鉴权）、
 * BusinessAttachmentService（删除时解绑附件）。本类不直接操作大字段或文件内容。</p>
 */
@Service
public class RequisitionService {
    /** 只有草稿和被驳回的申请允许改单，已提交的单据必须走撤回/取消。 */
    private static final List<String> EDITABLE_STATUSES = List.of("DRAFT", "REJECTED");
    /** 可取消集合；PENDING_APPROVAL 取消时还要终止在途审批流程，其余状态只改本地状态。 */
    private static final List<String> CANCELLABLE_STATUSES = List.of("DRAFT", "REJECTED", "PENDING_APPROVAL", "APPROVED");
    /** 只有草稿或已取消的申请可删，且是软删，明细与附件一并解绑。 */
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

    /** 列表不分页，按数据范围收敛后返回，供下拉与导出使用。 */
    public List<Map<String, Object>> list() {
        return mapper.list(dataScope.requisitionScope());
    }

    /**
     * 分页查询：页码夹到 ≥1、页大小夹到 1~100，状态统一转大写以兼容前端小写入参；
     * 关键字与状态为空即不过滤，数据范围只计算一次，count 与 page 用同一套条件保证总数一致。
     */
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

    /** 详情：先做数据范围校验（越权直接抛），主表查不到按 404；明细单独查询后挂到 items。 */
    public Map<String, Object> detail(Long id) {
        dataScope.requireRequisition(id);
        Map<String, Object> data = mapper.findDetail(id);
        if (data == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购申请不存在");
        data.put("items", mapper.findItems(id));
        return data;
    }

    /**
     * 建草稿：校验入参 → 取 PR 单号 → 写主表（金额由明细汇总，不用前端传）→ 按单号反查主键 → 写明细。
     * 单号由号段服务生成，事务回滚不会归还号段，因此失败重试会消耗号；insert 不回填主键，故用单号反查。
     */
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

    /**
     * 改单：加锁读 → 校验归属与可编辑状态 → 重新校验 → 回写表头 → 软删旧明细并整批重插。
     * 明细走“软删 + 重插”而不是逐条 diff，保证顺序与前端提交一致，也避免复用已删除的明细 id。
     */
    @Transactional
    public void update(Long id, RequisitionRequest r) {
        RequisitionLock row = lock(id);
        requireOwner(row, false);
        if (!EDITABLE_STATUSES.contains(row.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能编辑");
        }
        validate(r);
        Long user = SecurityUtils.currentUser().id();
        mapper.updateHeader(id, r.requiredDate(), r.purpose(), total(r.items()), user);
        mapper.softDeleteItems(id, user);
        insertItems(id, r.items(), user);
    }

    /**
     * 提交审批：加锁读并确认可编辑 → 预算校验（不足直接 409/400）→ 启动 Flowable 流程 → 回写状态与流程实例 id。
     * 流程可能在启动时即结束（无需审批节点的短路场景），此时直接落 APPROVED，否则落 PENDING_APPROVAL；
     * 预算校验与状态回写都在同一事务，流程启动失败会整体回滚。
     */
    @Transactional
    public void submit(Long id) {
        RequisitionLock row = lock(id);
        requireOwner(row, false);
        if (!EDITABLE_STATUSES.contains(row.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能提交");
        }
        budget.check(row.departmentId(), row.budgetAmount());
        String processId = workflow.startRequisition(id, row.budgetAmount(),
                SecurityUtils.currentUser());
        String nextStatus = workflow.isFinished(processId) ? "APPROVED" : "PENDING_APPROVAL";
        mapper.updateStatus(id, nextStatus, processId, SecurityUtils.currentUser().id());
    }

    /**
     * 取消：仅申请人可取消（管理员在此不放开），状态须在可取消集合内。
     * 处于审批中的单据先按业务键终止所有活动流程实例，再改本地状态，避免流程与单据状态脱节。
     */
    @Transactional
    public void cancel(Long id, ReasonRequest r) {
        RequisitionLock row = lock(id);
        requireOwner(row, true);
        String status = row.status();
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前状态不能取消");
        }
        if ("PENDING_APPROVAL".equals(status)) {
            workflow.cancelActiveProcesses("REQUISITION:" + id, r.reason());
        }
        mapper.cancel(id, r.reason().trim(), SecurityUtils.currentUser().id());
    }

    /** 软删：仅草稿或已取消可删；明细先软删、主单再软删，最后解绑业务附件（不删物理文件）。 */
    @Transactional
    public void delete(Long id) {
        RequisitionLock row = lock(id);
        requireOwner(row, true);
        if (!DELETABLE_STATUSES.contains(row.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有草稿或已取消申请可以删除");
        }
        Long user = SecurityUtils.currentUser().id();
        mapper.softDeleteItems(id, user);
        mapper.softDelete(id, user);
        attachments.unlinkBusiness("REQUISITION", id);
    }

    /**
     * 加锁读（SELECT ... FOR UPDATE）：写操作都先走它，用行锁把“读状态 → 判状态 → 写状态”串起来；
     * 查不到按 404 而不是让上层空指针。
     */
    private RequisitionLock lock(Long id) {
        RequisitionLock row = mapper.lockById(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "采购申请不存在");
        return row;
    }

    /**
     * 归属校验：默认只认申请人本人；adminAllowed 为 true 时额外放行 ADMIN（取消、删除保留给管理员兜底）。
     * 与数据范围校验分工不同：这里判“是不是你的单”，DataScopeService 判“你有没有资格看这条数据”。
     */
    private void requireOwner(RequisitionLock row, boolean adminAllowed) {
        CurrentUser user = SecurityUtils.currentUser();
        if (!row.applicantId().equals(user.id())
                && !(adminAllowed && user.roles().contains("ADMIN"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能操作自己的申请");
        }
    }

    /** 逐条插入明细而不是批量，保持前端提交顺序；条数由 validate 保证非空且物资不重复。 */
    private void insertItems(Long id, List<RequisitionItem> items, Long user) {
        for (RequisitionItem item : items) {
            mapper.insertItem(id, item.materialId(), item.quantity(), item.estimatedUnitPrice(), item.remark(), user);
        }
    }

    /**
     * 入参校验（不落库）：需求日期不早于今天、明细非空、数量 > 0 且单价 ≥ 0、同一物资不可重复、
     * 物资必须存在且启用。物资启用状态逐条查库，属有意为之的简单实现，明细多时是 N+1。
     */
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

    /** 明细金额汇总，单位元；quantity 与单价已在 validate 中保证非空，这里不再判空。 */
    private BigDecimal total(List<RequisitionItem> items) {
        return items.stream()
                .map(item -> item.quantity().multiply(item.estimatedUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    

    

    
}
