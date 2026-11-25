package com.procuremind.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.IssueMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.IssueRequest;
import com.procuremind.platform.pojo.row.BalanceLock;

/**
 * 出库/领用：扣减库存余额、批次与序列号，并向不可变流水写入 OUT 方向记录。
 *
 * <p>与收货对称：收货只增、出库只减，余额使用行锁更新，流水只追加不修改。</p>
 */
@Service
public class IssueService {
    private final IssueMapper mapper;
    private final DocumentNumberService numbers;

    public IssueService(IssueMapper mapper, DocumentNumberService numbers) {
        this.mapper = mapper;
        this.numbers = numbers;
    }

    @Transactional
    public Map<String, Object> issue(IssueRequest request) {
        var user = SecurityUtils.currentUser();
        Long warehouseId = request.warehouseId();
        Long materialId = request.materialId();
        BigDecimal quantity = request.quantity();
        if (mapper.countEnabledWarehouse(warehouseId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仓库不存在或已停用");
        }
        Map<String, Object> material = mapper.findMaterial(materialId);
        if (material == null) throw new BusinessException(HttpStatus.NOT_FOUND, "物资不存在");
        if (((Number) material.get("enabled")).intValue() != 1) throw new BusinessException(HttpStatus.BAD_REQUEST, "物资已停用");
        String mode = material.get("tracking_mode").toString();
        String batchNo = blank(request.batchNo());
        List<String> serials = request.serialNumbers() == null ? List.of() : request.serialNumbers();

        if ("SERIAL".equals(mode)) {
            if (quantity.stripTrailingZeros().scale() > 0) throw badRequest("序列号物资出库数量必须为整数");
            if (serials.size() != quantity.intValueExact()) throw badRequest("序列号数量必须与出库数量一致");
            if (new HashSet<>(serials).size() != serials.size()) throw badRequest("序列号不能重复");
            if (serials.stream().anyMatch(value -> value == null || value.isBlank())) throw badRequest("序列号不能为空");
        }
        if ("BATCH".equals(mode) && batchNo == null) throw badRequest("批次物资必须填写批次号");

        String no = numbers.next("OUT");
        Long departmentId = request.departmentId() == null ? user.departmentId() : request.departmentId();
        mapper.insertIssue(no, warehouseId, materialId, quantity, batchNo, json(serials), request.purpose().trim(),
                departmentId, user.id(), Instant.now(), blank(request.remark()));
        Long issueId = mapper.findIdByNo(no);

        if ("SERIAL".equals(mode)) {
            for (String serial : serials) {
                if (mapper.issueSerial(warehouseId, materialId, serial.trim()) == 0) {
                    throw new BusinessException(HttpStatus.CONFLICT, "序列号不存在、已出库或不属于该仓库：" + serial);
                }
            }
        }
        if ("BATCH".equals(mode)) {
            if (mapper.decrementBatch(warehouseId, materialId, batchNo, quantity) == 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "批次库存不足或批次号不存在");
            }
        }

        BigDecimal balance = decrementBalance(warehouseId, materialId, quantity, user.id());
        mapper.insertLedger(warehouseId, materialId, issueId, quantity, balance, batchNo, user.id());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(issueId));
        result.put("issueNo", no);
        result.put("balance", balance.toPlainString());
        result.put("status", "ISSUED");
        return result;
    }

    public Map<String, Object> page(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        int total = mapper.countPage(like);
        List<Map<String, Object>> records = mapper.page(like, pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) (((long) total + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> detail(Long id) {
        Map<String, Object> issue = mapper.findDetail(id);
        if (issue == null) throw new BusinessException(HttpStatus.NOT_FOUND, "出库单不存在");
        return issue;
    }

    private BigDecimal decrementBalance(Long warehouseId, Long materialId, BigDecimal quantity, Long user) {
        BalanceLock row = mapper.lockBalance(warehouseId, materialId);
        if (row == null) throw new BusinessException(HttpStatus.CONFLICT, "该仓库没有此物资的库存记录");
        BigDecimal current = row.quantity();
        if (current.compareTo(quantity) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "库存不足，当前可用 " + current.stripTrailingZeros().toPlainString());
        }
        BigDecimal next = current.subtract(quantity);
        mapper.updateBalance(row.id(), next, user, row.version());
        return next;
    }

    private String json(List<String> serials) {
        try { return serials.isEmpty() ? null : new ObjectMapper().writeValueAsString(serials); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException badRequest(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }

    
}
