package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.ContractMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.ContractCreateRequest;
import com.procuremind.platform.pojo.request.ContractUpdateRequest;

/**
 * 合同管理：供应商合同、可选关联采购订单、到期自动标记。
 *
 * <p>状态机：DRAFT --启用--> ACTIVE --到期--> EXPIRED；ACTIVE/EXPIRED 可手动终止为 TERMINATED；
 * 只有 DRAFT 可删除（软删）。合同供应商必须与所关联采购订单的供应商一致，校验在 validate 里统一做。
 *
 * <p>依赖 ContractMapper（查询与状态更新）与 DocumentNumberService（CT 单号，也允许前台自带编号）。
 * 本类不做数据范围过滤，可见性由接口层决定；到期不是定时任务，而是在分页查询前惰性刷一次。</p>
 */
@Service
public class ContractService {
    private final ContractMapper mapper;
    private final DocumentNumberService numbers;

    public ContractService(ContractMapper mapper, DocumentNumberService numbers) {
        this.mapper = mapper;
        this.numbers = numbers;
    }

    /**
     * 合同分页：查之前先落一次到期标记，因此用户看到列表时状态已是“惰性刷新”后的结果；
     * 页码/页大小做归一化，状态转大写，关键字为空用 "%%" 匹配全部。写操作放在只读查询前，无外层事务。
     */
    public Map<String, Object> page(String keyword, String status, int current, int size) {
        expireDueContracts();
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String state = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        int total = mapper.countPage(like, state);
        List<Map<String, Object>> records = mapper.page(like, state, pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) (((long) total + pageSize - 1) / pageSize));
        return result;
    }

    /** 合同详情，查不到按 404。 */
    public Map<String, Object> detail(Long id) {
        Map<String, Object> contract = mapper.findDetail(id);
        if (contract == null) throw new BusinessException(HttpStatus.NOT_FOUND, "合同不存在");
        return contract;
    }

    /**
     * 新建合同：校验通过后落库；合同编号允许前台自带（线下已签的合同沿用原号），为空才取 CT 号。
     * 新建一律是 DRAFT，不在这里直接启用；insert 不回填主键，故按编号反查 id。
     */
    @Transactional
    public Long create(ContractCreateRequest request) {
        validate(request);
        Long user = SecurityUtils.currentUser().id();
        String no = request.contractNo() == null || request.contractNo().isBlank()
                ? numbers.next("CT") : request.contractNo().trim();
        mapper.insert(no, request.name().trim(), request.supplierId(), request.purchaseOrderId(), request.type(),
                request.amount(), request.signedDate(), request.startDate(), request.endDate(),
                request.fileId(), blank(request.remark()), user);
        return mapper.findIdByNo(no);
    }

    /**
     * 修改合同：只有 DRAFT / ACTIVE 可改，已到期或已终止的合同内容冻结（保留审计意义上的原貌）。
     * 不允许改编号与状态，状态只能走 activate/terminate。
     */
    @Transactional
    public void update(Long id, ContractUpdateRequest request) {
        Map<String, Object> contract = require(id);
        if (!List.of("DRAFT", "ACTIVE").contains(contract.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "已到期或已终止的合同不能修改");
        }
        validate(request);
        mapper.update(id, request.name().trim(), request.supplierId(), request.purchaseOrderId(), request.type(),
                request.amount(), request.signedDate(), request.startDate(), request.endDate(), request.fileId(),
                blank(request.remark()), SecurityUtils.currentUser().id());
    }

    /** 启用：只允许 DRAFT → ACTIVE；启用后合同才计入“生效中”口径。 */
    @Transactional
    public void activate(Long id) {
        Map<String, Object> contract = require(id);
        if (!"DRAFT".equals(contract.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有草稿合同可以启用");
        mapper.activate(id, SecurityUtils.currentUser().id());
    }

    /** 终止：ACTIVE 或已到期的合同可手动终止；终止是终态，不可再启用或修改。 */
    @Transactional
    public void terminate(Long id) {
        Map<String, Object> contract = require(id);
        if (!List.of("ACTIVE", "EXPIRED").contains(contract.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有生效或已到期合同可以终止");
        }
        mapper.terminate(id, SecurityUtils.currentUser().id());
    }

    /** 软删：仅草稿可删，已生效合同只能终止不能删除，保证合同台账可追溯。 */
    @Transactional
    public void delete(Long id) {
        Map<String, Object> contract = require(id);
        if (!"DRAFT".equals(contract.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有草稿合同可以删除");
        mapper.softDelete(id, SecurityUtils.currentUser().id());
    }

    /**
     * 惰性到期：把 end_date 已过且仍为 ACTIVE 的合同批量刷成 EXPIRED。
     * 挂在分页查询前执行，等价于“有人看才结算”，项目里没有独立的定时任务。
     */
    private void expireDueContracts() {
        mapper.expireDue();
    }

    /** 取合同状态（只查必要列），查不到按 404；所有状态流转都先走它。 */
    private Map<String, Object> require(Long id) {
        Map<String, Object> row = mapper.findStatus(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "合同不存在");
        return row;
    }

    /**
     * 创建与更新共用一套校验，故用 Object 接收后按类型取值（两个请求的字段结构一致）。
     * 三条硬约束：供应商必须存在；关联订单必须存在且供应商与合同一致；开始日期不能晚于结束日期。
     */
    private void validate(Object request) {
        Long supplierId;
        Long purchaseOrderId;
        LocalDate startDate;
        LocalDate endDate;
        if (request instanceof ContractCreateRequest create) {
            supplierId = create.supplierId(); purchaseOrderId = create.purchaseOrderId();
            startDate = create.startDate(); endDate = create.endDate();
        } else {
            ContractUpdateRequest update = (ContractUpdateRequest) request;
            supplierId = update.supplierId(); purchaseOrderId = update.purchaseOrderId();
            startDate = update.startDate(); endDate = update.endDate();
        }
        if (mapper.countSupplier(supplierId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "供应商不存在");
        }
        if (purchaseOrderId != null) {
            Long orderSupplierId = mapper.findOrderSupplier(purchaseOrderId);
            if (orderSupplierId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "关联的采购订单不存在");
            if (orderSupplierId != supplierId) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "合同供应商与采购订单供应商不一致");
            }
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "合同开始日期不能晚于结束日期");
        }
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    

    
}
