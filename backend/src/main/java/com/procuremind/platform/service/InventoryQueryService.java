package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.InventoryQueryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryQueryService {
    private final InventoryQueryMapper mapper;

    public InventoryQueryService(InventoryQueryMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> receipts() {
        return mapper.receipts();
    }

    public Map<String, Object> page(String orderNo, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String keyword = orderNo == null || orderNo.isBlank() ? null : "%" + orderNo.trim() + "%";
        int total = mapper.countReceiptPage(keyword);
        List<Map<String, Object>> records = mapper.receiptPage(keyword, pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) (((long) total + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> receipt(Long id) {
        Map<String, Object> detail = mapper.findReceipt(id);
        if (detail == null) throw new BusinessException(HttpStatus.NOT_FOUND, "收货单不存在");
        detail.put("items", mapper.findReceiptItems(id));
        return detail;
    }

    public Map<String, Object> balancesPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        int total = mapper.countBalances(like);
        List<Map<String, Object>> records = mapper.balancesPage(like, pageSize, (page - 1) * pageSize);
        return pageResult(records, total, page, pageSize);
    }

    public Map<String, Object> ledgerPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        int total = mapper.countLedger(like);
        List<Map<String, Object>> records = mapper.ledgerPage(like, pageSize, (page - 1) * pageSize);
        return pageResult(records, total, page, pageSize);
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, int total, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) (((long) total + pageSize - 1) / pageSize));
        return result;
    }

    public List<Map<String, Object>> balances() {
        return mapper.balances();
    }

    public List<Map<String, Object>> ledger() {
        return mapper.ledger();
    }
}
