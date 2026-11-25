package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.InventoryQueryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存域的只读查询：收货单列表/明细、库存余额与出入库流水台账。
 *
 * <p>边界：本类只查不写，不扣减库存、不推进单据状态、不校验数据范围（调用方接口层已按角色收敛）；
 * 入库引起的余额变化由 ReceiptService 负责。所有列表都做了分页参数归一化与关键字转义，依赖
 * InventoryQueryMapper 的只读 SQL。</p>
 */
@Service
public class InventoryQueryService {
    private final InventoryQueryMapper mapper;

    public InventoryQueryService(InventoryQueryMapper mapper) {
        this.mapper = mapper;
    }

    /** 全量收货单，供下拉选择/导出使用，不分页；数据量大时应改用 page()。 */
    public List<Map<String, Object>> receipts() {
        return mapper.receipts();
    }

    /**
     * 收货单分页：页码小于 1 归 1，每页夹在 1~100；订单号关键字在 Java 侧补 % 通配，
     * 空白串转 null 表示不过滤（下游 SQL 用动态条件，不走全表 LIKE）。
     */
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

    /** 收货单详情：主表查不到即 404，明细在同一事务外二次查询，保证 items 至少为空列表。 */
    public Map<String, Object> receipt(Long id) {
        Map<String, Object> detail = mapper.findReceipt(id);
        if (detail == null) throw new BusinessException(HttpStatus.NOT_FOUND, "收货单不存在");
        detail.put("items", mapper.findReceiptItems(id));
        return detail;
    }

    /**
     * 库存余额分页：与收货单分页不同，关键字为空时用 "%%" 匹配全部，SQL 因此可以固定写 LIKE，
     * 不必拼动态条件；页大小同样夹在 1~100，防止前端传大页拖垮查询。
     */
    public Map<String, Object> balancesPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        int total = mapper.countBalances(like);
        List<Map<String, Object>> records = mapper.balancesPage(like, pageSize, (page - 1) * pageSize);
        return pageResult(records, total, page, pageSize);
    }

    /** 出入库流水分页，口径与库存余额一致：按物资编码/名称模糊匹配，时间范围由调用方在下游扩展。 */
    public Map<String, Object> ledgerPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        int total = mapper.countLedger(like);
        List<Map<String, Object>> records = mapper.ledgerPage(like, pageSize, (page - 1) * pageSize);
        return pageResult(records, total, page, pageSize);
    }

    /**
     * 统一分页外壳：总页数用 long 中间值再转 int，避免 total 接近 Integer.MAX_VALUE 时加法溢出成负数。
     */
    private Map<String, Object> pageResult(List<Map<String, Object>> records, int total, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) (((long) total + pageSize - 1) / pageSize));
        return result;
    }

    /** 全量库存余额，供看板/导出使用，不分页。 */
    public List<Map<String, Object>> balances() {
        return mapper.balances();
    }

    /** 全量出入库流水，供导出使用；页面展示应走 ledgerPage 以免一次拉全表。 */
    public List<Map<String, Object>> ledger() {
        return mapper.ledger();
    }
}
