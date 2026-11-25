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

/** 合同管理：供应商合同、可选关联采购订单、到期自动标记。 */
@Service
public class ContractService {
    private final ContractMapper mapper;
    private final DocumentNumberService numbers;

    public ContractService(ContractMapper mapper, DocumentNumberService numbers) {
        this.mapper = mapper;
        this.numbers = numbers;
    }

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

    public Map<String, Object> detail(Long id) {
        Map<String, Object> contract = mapper.findDetail(id);
        if (contract == null) throw new BusinessException(HttpStatus.NOT_FOUND, "合同不存在");
        return contract;
    }

    @Transactional
    public Long create(CreateRequest request) {
        validate(request);
        Long user = SecurityUtils.currentUser().id();
        String no = request.contractNo() == null || request.contractNo().isBlank()
                ? numbers.next("CT") : request.contractNo().trim();
        mapper.insert(no, request.name().trim(), request.supplierId(), request.purchaseOrderId(), request.type(),
                request.amount(), request.signedDate(), request.startDate(), request.endDate(),
                request.fileId(), blank(request.remark()), user);
        return mapper.findIdByNo(no);
    }

    @Transactional
    public void update(Long id, UpdateRequest request) {
        Map<String, Object> contract = require(id);
        if (!List.of("DRAFT", "ACTIVE").contains(contract.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "已到期或已终止的合同不能修改");
        }
        validate(request);
        mapper.update(id, request.name().trim(), request.supplierId(), request.purchaseOrderId(), request.type(),
                request.amount(), request.signedDate(), request.startDate(), request.endDate(), request.fileId(),
                blank(request.remark()), SecurityUtils.currentUser().id());
    }

    @Transactional
    public void activate(Long id) {
        Map<String, Object> contract = require(id);
        if (!"DRAFT".equals(contract.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有草稿合同可以启用");
        mapper.activate(id, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void terminate(Long id) {
        Map<String, Object> contract = require(id);
        if (!List.of("ACTIVE", "EXPIRED").contains(contract.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有生效或已到期合同可以终止");
        }
        mapper.terminate(id, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void delete(Long id) {
        Map<String, Object> contract = require(id);
        if (!"DRAFT".equals(contract.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有草稿合同可以删除");
        mapper.softDelete(id, SecurityUtils.currentUser().id());
    }

    private void expireDueContracts() {
        mapper.expireDue();
    }

    private Map<String, Object> require(Long id) {
        Map<String, Object> row = mapper.findStatus(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "合同不存在");
        return row;
    }

    private void validate(Object request) {
        Long supplierId;
        Long purchaseOrderId;
        LocalDate startDate;
        LocalDate endDate;
        if (request instanceof CreateRequest create) {
            supplierId = create.supplierId(); purchaseOrderId = create.purchaseOrderId();
            startDate = create.startDate(); endDate = create.endDate();
        } else {
            UpdateRequest update = (UpdateRequest) request;
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

    public record CreateRequest(
            @Size(max = 50) String contractNo,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Positive Long supplierId,
            Long purchaseOrderId,
            @NotBlank @Pattern(regexp = "FRAMEWORK|PURCHASE|SERVICE|NDA") String type,
            @NotNull @DecimalMin("0") BigDecimal amount,
            LocalDate signedDate, LocalDate startDate, LocalDate endDate,
            Long fileId,
            @Size(max = 500) String remark) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @Positive Long supplierId,
            Long purchaseOrderId,
            @NotBlank @Pattern(regexp = "FRAMEWORK|PURCHASE|SERVICE|NDA") String type,
            @NotNull @DecimalMin("0") BigDecimal amount,
            LocalDate signedDate, LocalDate startDate, LocalDate endDate,
            Long fileId,
            @Size(max = 500) String remark) {}
}
