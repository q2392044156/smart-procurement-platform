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

    @Transactional
    public Map<String, Object> receive(String key, ReceiptRequest r) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "必须提供不超过100字符的Idempotency-Key");
        }
        Long user = SecurityUtils.currentUser().id();
        try {
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

        Map<String, Object> order = mapper.lockOrder(r.purchaseOrderId());
        if (order == null || !List.of("APPROVED", "PARTIALLY_RECEIVED").contains(order.get("status").toString())) {
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
            Map<String, Object> item = mapper.lockOrderItem(input.orderItemId(), r.purchaseOrderId());
            if (item == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "订单明细不存在");
            }
            if (input.quantity() == null || input.quantity().signum() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "收货数量必须大于0");
            }
            BigDecimal remaining = ((BigDecimal) item.get("quantity")).subtract((BigDecimal) item.get("received_quantity"));
            if (input.quantity().compareTo(remaining) > 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "收货数量超过订单剩余数量");
            }
            String mode = item.get("tracking_mode").toString();
            validateTracking(mode, input);
            Long materialId = ((Number) item.get("material_id")).longValue();
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

    private String write(Object value) {
        try {
            return value == null ? null : json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    

    
}
