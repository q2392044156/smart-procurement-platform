package com.procuremind.platform.contract;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.DocumentNumberService;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 合同管理：供应商合同、可选关联采购订单、到期自动标记。 */
@Service
public class ContractService {
    private final JdbcTemplate jdbc;
    private final DocumentNumberService numbers;

    public ContractService(JdbcTemplate jdbc, DocumentNumberService numbers) {
        this.jdbc = jdbc;
        this.numbers = numbers;
    }

    public Map<String, Object> page(String keyword, String status, int current, int size) {
        expireDueContracts();
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String state = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String clause = "c.deleted=0 and (c.contract_no like ? or c.name like ? or s.name like ?) and (? is null or c.status=?)";
        Long total = jdbc.queryForObject("""
                select count(*) from ct_contract c join sp_supplier s on s.id=c.supplier_id where """ + " " + clause,
                Long.class, like, like, like, state, state);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(c.id as char) id,c.contract_no contractNo,c.name,cast(c.supplier_id as char) supplierId,
                       s.name supplier,c.type,c.amount,c.signed_date signedDate,c.start_date startDate,c.end_date endDate,
                       c.status,cast(c.purchase_order_id as char) purchaseOrderId,o.order_no orderNo,
                       c.created_at createdAt
                from ct_contract c
                join sp_supplier s on s.id=c.supplier_id
                left join po_purchase_order o on o.id=c.purchase_order_id
                where """ + " " + clause + " order by c.id desc limit ? offset ?",
                like, like, like, state, state, pageSize, (page - 1) * pageSize);
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
        Map<String, Object> contract = jdbc.queryForMap("""
                select cast(c.id as char) id,c.contract_no contractNo,c.name,cast(c.supplier_id as char) supplierId,
                       s.name supplier,c.type,c.amount,c.signed_date signedDate,c.start_date startDate,c.end_date endDate,
                       c.status,cast(c.purchase_order_id as char) purchaseOrderId,o.order_no orderNo,
                       cast(c.file_id as char) fileId,f.original_name fileName,c.remark,c.created_at createdAt
                from ct_contract c
                join sp_supplier s on s.id=c.supplier_id
                left join po_purchase_order o on o.id=c.purchase_order_id
                left join fs_file_metadata f on f.id=c.file_id and f.deleted=0
                where c.id=? and c.deleted=0
                """, id);
        return contract;
    }

    @Transactional
    public Long create(CreateRequest request) {
        validate(request);
        Long user = SecurityUtils.currentUser().id();
        String no = request.contractNo() == null || request.contractNo().isBlank()
                ? numbers.next("CT") : request.contractNo().trim();
        jdbc.update("""
                insert into ct_contract(contract_no,name,supplier_id,purchase_order_id,type,amount,signed_date,start_date,
                    end_date,status,file_id,remark,created_by,updated_by)
                values(?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?)
                """, no, request.name().trim(), request.supplierId(), request.purchaseOrderId(), request.type(),
                request.amount(), request.signedDate(), request.startDate(), request.endDate(),
                request.fileId(), blank(request.remark()), user, user);
        return jdbc.queryForObject("select id from ct_contract where contract_no=?", Long.class, no);
    }

    @Transactional
    public void update(Long id, UpdateRequest request) {
        Map<String, Object> contract = require(id);
        if (!List.of("DRAFT", "ACTIVE").contains(contract.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "已到期或已终止的合同不能修改");
        }
        validate(request);
        jdbc.update("""
                update ct_contract set name=?,supplier_id=?,purchase_order_id=?,type=?,amount=?,signed_date=?,start_date=?,
                    end_date=?,file_id=?,remark=?,updated_by=? where id=? and deleted=0
                """, request.name().trim(), request.supplierId(), request.purchaseOrderId(), request.type(),
                request.amount(), request.signedDate(), request.startDate(), request.endDate(), request.fileId(),
                blank(request.remark()), SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void activate(Long id) {
        Map<String, Object> contract = require(id);
        if (!"DRAFT".equals(contract.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有草稿合同可以启用");
        jdbc.update("update ct_contract set status='ACTIVE',updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void terminate(Long id) {
        Map<String, Object> contract = require(id);
        if (!List.of("ACTIVE", "EXPIRED").contains(contract.get("status").toString())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有生效或已到期合同可以终止");
        }
        jdbc.update("update ct_contract set status='TERMINATED',updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void delete(Long id) {
        Map<String, Object> contract = require(id);
        if (!"DRAFT".equals(contract.get("status"))) throw new BusinessException(HttpStatus.CONFLICT, "只有草稿合同可以删除");
        jdbc.update("update ct_contract set deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    private void expireDueContracts() {
        jdbc.update("update ct_contract set status='EXPIRED' where status='ACTIVE' and end_date is not null and end_date<curdate() and deleted=0");
    }

    private Map<String, Object> require(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select status,deleted from ct_contract where id=? and deleted=0", id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "合同不存在");
        return rows.getFirst();
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
        if (count("select count(*) from sp_supplier where id=? and deleted=0", supplierId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "供应商不存在");
        }
        if (purchaseOrderId != null) {
            List<Map<String, Object>> orders = jdbc.queryForList(
                    "select supplier_id from po_purchase_order where id=? and deleted=0", purchaseOrderId);
            if (orders.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "关联的采购订单不存在");
            if (((Number) orders.getFirst().get("supplier_id")).longValue() != supplierId) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "合同供应商与采购订单供应商不一致");
            }
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "合同开始日期不能晚于结束日期");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
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
