package com.procuremind.platform.inventory;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.DocumentNumberService;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库/领用：扣减库存余额、批次与序列号，并向不可变流水写入 OUT 方向记录。
 *
 * <p>与收货对称：收货只增、出库只减，余额使用行锁更新，流水只追加不修改。</p>
 */
@Service
public class IssueService {
    private final JdbcTemplate jdbc;
    private final DocumentNumberService numbers;

    public IssueService(JdbcTemplate jdbc, DocumentNumberService numbers) {
        this.jdbc = jdbc;
        this.numbers = numbers;
    }

    @Transactional
    public Map<String, Object> issue(IssueRequest request) {
        var user = SecurityUtils.currentUser();
        Long warehouseId = request.warehouseId();
        Long materialId = request.materialId();
        BigDecimal quantity = request.quantity();
        if (count("select count(*) from md_warehouse where id=? and enabled=1 and deleted=0", warehouseId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仓库不存在或已停用");
        }
        Map<String, Object> material = jdbc.queryForMap("select tracking_mode,enabled from md_material where id=? and deleted=0", materialId);
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
        jdbc.update("""
                insert into inv_issue(issue_no,warehouse_id,material_id,quantity,batch_no,serial_numbers_json,purpose,
                    department_id,requester_id,issued_by,issued_at,remark,created_by,updated_by)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, no, warehouseId, materialId, quantity, batchNo, json(serials), request.purpose().trim(),
                departmentId, user.id(), user.id(), Instant.now(), blank(request.remark()), user.id(), user.id());
        Long issueId = jdbc.queryForObject("select id from inv_issue where issue_no=?", Long.class, no);

        if ("SERIAL".equals(mode)) {
            for (String serial : serials) {
                int updated = jdbc.update("""
                        update inv_serial set status='ISSUED'
                        where warehouse_id=? and material_id=? and serial_no=? and status='IN_STOCK'
                        """, warehouseId, materialId, serial.trim());
                if (updated == 0) throw new BusinessException(HttpStatus.CONFLICT, "序列号不存在、已出库或不属于该仓库：" + serial);
            }
        }
        if ("BATCH".equals(mode)) {
            int updated = jdbc.update("""
                    update inv_batch set quantity=quantity-?
                    where warehouse_id=? and material_id=? and batch_no=? and quantity>=?
                    """, quantity, warehouseId, materialId, batchNo, quantity);
            if (updated == 0) throw new BusinessException(HttpStatus.CONFLICT, "批次库存不足或批次号不存在");
        }

        BigDecimal balance = decrementBalance(warehouseId, materialId, quantity, user.id());
        jdbc.update("""
                insert into inv_ledger(warehouse_id,material_id,business_type,business_id,direction,quantity,balance_after,batch_no,created_by,created_at)
                values(?,?,'ISSUE',?,'OUT',?,?,?,?,utc_timestamp(6))
                """, warehouseId, materialId, issueId, quantity, balance, batchNo, user.id());

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
        String clause = "i.deleted=0 and (i.issue_no like ? or m.code like ? or m.name like ?)";
        Long total = jdbc.queryForObject("""
                select count(*) from inv_issue i join md_material m on m.id=i.material_id where """ + " " + clause, Long.class, like, like, like);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(i.id as char) id,i.issue_no issueNo,w.name warehouse,m.code materialCode,m.name materialName,
                       i.quantity,i.batch_no batchNo,i.purpose,d.name department,u.display_name requester,
                       i.issued_at issuedAt,i.remark
                from inv_issue i
                join md_warehouse w on w.id=i.warehouse_id
                join md_material m on m.id=i.material_id
                join sys_department d on d.id=i.department_id
                join sys_user u on u.id=i.requester_id
                where """ + " " + clause + " order by i.id desc limit ? offset ?",
                like, like, like, pageSize, (page - 1) * pageSize);
        long totalCount = total == null ? 0 : total;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) totalCount);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((totalCount + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> detail(Long id) {
        Map<String, Object> issue = jdbc.queryForMap("""
                select cast(i.id as char) id,i.issue_no issueNo,w.name warehouse,cast(i.warehouse_id as char) warehouseId,
                       m.code materialCode,m.name materialName,m.tracking_mode trackingMode,cast(i.material_id as char) materialId,
                       i.quantity,i.batch_no batchNo,i.serial_numbers_json serialNumbers,i.purpose,
                       d.name department,u.display_name requester,i.issued_at issuedAt,i.remark
                from inv_issue i
                join md_warehouse w on w.id=i.warehouse_id
                join md_material m on m.id=i.material_id
                join sys_department d on d.id=i.department_id
                join sys_user u on u.id=i.requester_id
                where i.id=? and i.deleted=0
                """, id);
        return issue;
    }

    private BigDecimal decrementBalance(Long warehouseId, Long materialId, BigDecimal quantity, Long user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id,quantity,version from inv_balance where warehouse_id=? and material_id=? and deleted=0 for update
                """, warehouseId, materialId);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "该仓库没有此物资的库存记录");
        Map<String, Object> row = rows.getFirst();
        BigDecimal current = (BigDecimal) row.get("quantity");
        if (current.compareTo(quantity) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "库存不足，当前可用 " + current.stripTrailingZeros().toPlainString());
        }
        BigDecimal next = current.subtract(quantity);
        jdbc.update("update inv_balance set quantity=?,version=version+1,updated_by=? where id=? and version=?",
                next, user, row.get("id"), row.get("version"));
        return next;
    }

    private String json(List<String> serials) {
        try { return serials.isEmpty() ? null : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(serials); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException badRequest(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }

    public record IssueRequest(
            @NotNull @Positive Long warehouseId,
            @NotNull @Positive Long materialId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Size(max = 100) String batchNo,
            List<@Size(max = 200) String> serialNumbers,
            @NotBlank @Size(max = 200) String purpose,
            Long departmentId,
            @Size(max = 500) String remark) {}
}
