package com.procuremind.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.ReceiptMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.procuremind.platform.pojo.request.ReceiptItem;
import com.procuremind.platform.pojo.request.ReceiptRequest;
import com.procuremind.platform.pojo.row.PurchaseOrderStatus;
import com.procuremind.platform.pojo.row.ReceiptItemLock;

/**
 * 收货入库：按订单明细收货，增加已收数量与库存余额，登记批次/序列号并写不可变流水，最后回写订单状态。
 *
 * <p>幂等：入口必须带 Idempotency-Key。先用唯一键“占位”，冲突即视为重复请求——已完成则原样重放上次响应，
 * 处理中则返回 409，避免重复入库。幂等键与流水同一事务落库，回滚后键也一并回滚，可安全重试。
 *
 * <p>并发控制：订单表、订单明细、库存余额都先加行锁或做条件更新；余额更新用 version 乐观锁重试 3 次。
 * 追踪模式（SERIAL/BATCH/NONE）与出库共用同一套语义。依赖 ReceiptMapper、ObjectMapper（序列号 JSON）、
 * DocumentNumberService（GRN 单号）。</p>
 */
@Service
public class ReceiptService {
    private final ReceiptMapper mapper;
    private final ObjectMapper json;
    private final DocumentNumberService numbers;

    public ReceiptService(ReceiptMapper mapper, ObjectMapper json, DocumentNumberService numbers) {
        this.mapper = mapper;
        this.json = json;
        this.numbers = numbers;
    }

    /**
     * 收货主流程：占幂等键 → 锁订单并校验状态（仅已审批/部分收货可收）→ 校验仓库与明细行不重复
     * → 落收货单 → 逐行锁订单明细、校验剩余量与追踪信息、增已收数量、增余额、登记批次/序列号、写流水
     * → 重算订单状态（全部收完 RECEIVED，否则 PARTIALLY_RECEIVED）→ 把响应写入幂等记录。
     * 所有步骤同一事务，任一行失败整单回滚，不会出现“收了一半”的收货单。
     */
    @Transactional
    public Map<String, Object> receive(String key, ReceiptRequest r) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "必须提供不超过100字符的Idempotency-Key");
        }
        Long user = SecurityUtils.currentUser().id();
        try {
            // 先占位写幂等键：唯一键冲突是“重复请求”的唯一可靠判据，冲突后再区分已完成与处理中。
            mapper.insertIdempotency(key, user, "CREATE_RECEIPT");
        } catch (DuplicateKeyException e) {
            Map<String, Object> row = mapper.findIdempotency(key, user, "CREATE_RECEIPT");
            if (row != null && "COMPLETED".equals(row.get("status"))) {
                try {
                    return json.readValue(row.get("response_json").toString(), new TypeReference<>() {
                    });
                } catch (Exception ex) {
                    throw new BusinessException(HttpStatus.CONFLICT, "幂等结果解析失败");
                }
            }
            throw new BusinessException(HttpStatus.CONFLICT, "相同请求正在处理中");
        }

        PurchaseOrderStatus order = mapper.lockOrder(r.purchaseOrderId());
        if (order == null || !List.of("APPROVED", "PARTIALLY_RECEIVED").contains(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "仅已审批或部分收货订单允许收货");
        }
        if (mapper.countEnabledWarehouse(r.warehouseId()) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仓库不存在或已停用");
        }
        if (r.items() == null || r.items().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收货明细不能为空");
        }
        Set<Long> orderItems = new HashSet<>();
        for (ReceiptItem item : r.items()) {
            if (!orderItems.add(item.orderItemId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "同一订单明细不能重复收货");
            }
        }

        String no = numbers.next("GRN");
        mapper.insertReceipt(no, r.purchaseOrderId(), r.warehouseId(), user, Instant.now(), r.remark());
        Long receiptId = mapper.findIdByNo(no);

        for (ReceiptItem input : r.items()) {
            ReceiptItemLock item = mapper.lockOrderItem(input.orderItemId(), r.purchaseOrderId());
            if (item == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "订单明细不存在");
            }
            if (input.quantity() == null || input.quantity().signum() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "收货数量必须大于0");
            }
            BigDecimal remaining = (item.quantity()).subtract(item.receivedQuantity());
            if (input.quantity().compareTo(remaining) > 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "收货数量超过订单剩余数量");
            }
            String mode = item.trackingMode();
            validateTracking(mode, input);
            Long materialId = item.materialId();
            String serialJson = write(input.serialNumbers());
            mapper.insertReceiptItem(receiptId, input.orderItemId(), materialId, input.quantity(), input.batchNo(), serialJson, user);
            Long receiptItemId = mapper.lastInsertId();
            if (mapper.increaseReceived(input.orderItemId(), input.quantity(), user) != 1) {
                throw new BusinessException(HttpStatus.CONFLICT, "订单明细已被并发更新，请重试");
            }
            BigDecimal balance = addBalance(r.warehouseId(), materialId, input.quantity(), user);
            if ("BATCH".equals(mode)) {
                mapper.upsertBatch(r.warehouseId(), materialId, input.batchNo(), input.quantity());
            }
            if ("SERIAL".equals(mode)) {
                for (String serial : input.serialNumbers()) {
                    try {
                        mapper.insertSerial(r.warehouseId(), materialId, serial, receiptItemId);
                    } catch (DuplicateKeyException e) {
                        throw new BusinessException(HttpStatus.CONFLICT, "序列号已存在：" + serial);
                    }
                }
            }
            mapper.insertLedger(r.warehouseId(), materialId, receiptId, input.quantity(), balance, input.batchNo(), user, key);
        }

        Long unfinished = mapper.countUnfinished(r.purchaseOrderId());
        String status = unfinished == 0 ? "RECEIVED" : "PARTIALLY_RECEIVED";
        mapper.updateOrderStatus(r.purchaseOrderId(), status, user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", receiptId);
        response.put("receiptNo", no);
        response.put("orderStatus", status);
        mapper.completeIdempotency(key, user, "CREATE_RECEIPT", write(response));
        return response;
    }

    /**
     * 增加库存余额：先补一行（不存在则插 0），再循环“读 → 加 → 带 version 条件更新”，最多重试 3 次。
     * 之所以自己重试而不加行锁：收货是热点写，乐观锁冲突时重试比排队等待更划算；3 次仍冲突按 409 让调用方重试。
     */
    private BigDecimal addBalance(Long warehouse, Long material, BigDecimal quantity, Long user) {
        mapper.insertBalance(warehouse, material, user);
        for (int i = 0; i < 3; i++) {
            Map<String, Object> b = mapper.findBalance(warehouse, material);
            BigDecimal next = ((BigDecimal) b.get("quantity")).add(quantity);
            if (mapper.updateBalance(((Number) b.get("id")).longValue(), next, user, b.get("version")) == 1) {
                return next;
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT, "库存被并发更新，请重试");
    }

    /**
     * 按物资追踪模式校验收货行：BATCH 必须有批次号；SERIAL 要求数量为整数、序列号条数一致、非空、不重复。
     * 与 IssueService 的校验规则对称，出库时还会再查一次“序列号是否存在且未出库”。
     */
    private void validateTracking(String mode, ReceiptItem item) {
        if ("BATCH".equals(mode) && (item.batchNo() == null || item.batchNo().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "批次物资必须填写批次号");
        }
        if ("SERIAL".equals(mode)) {
            if (item.quantity().stripTrailingZeros().scale() > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "序列号物资数量必须为整数");
            }
            if (item.serialNumbers() == null || item.serialNumbers().size() != item.quantity().intValueExact()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "序列号数量必须与收货数量一致");
            }
            if (item.serialNumbers().stream().anyMatch(v -> v == null || v.isBlank())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "序列号不能为空");
            }
            if (new HashSet<>(item.serialNumbers()).size() != item.serialNumbers().size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "序列号不能重复");
            }
        }
    }

    /** 统一 JSON 序列化（序列号列表存单列、幂等响应存快照）；失败属程序错误，包成 IllegalStateException 回滚。 */
    private String write(Object value) {
        try {
            return value == null ? null : json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    

    
}
