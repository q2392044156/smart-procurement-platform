package com.procuremind.platform.agentintegration;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.idempotency.IdempotencyService;
import com.procuremind.platform.masterdata.MasterDataService;
import com.procuremind.platform.purchaseorder.PurchaseOrderService;
import com.procuremind.platform.supplier.SupplierService;
import com.procuremind.platform.system.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧 MCP 工具协议的内部适配层。仅内部令牌认证可以访问，不经 Nginx 对外暴露。
 *
 * <p>写操作一律复用领域 Service 的校验与事务，不再直接拼 SQL；状态出入参统一为平台状态字符串，
 * 入参兼容历史数字编码（见 {@link LegacyStatus}）。</p>
 */
@RestController
@RequestMapping("/api")
public class LegacyErpController {
    private final JdbcTemplate jdbc;
    private final PurchaseOrderService orders;
    private final SupplierService suppliers;
    private final MasterDataService masterData;
    private final DataScopeService dataScope;
    private final IdempotencyService idempotency;

    public LegacyErpController(JdbcTemplate jdbc, PurchaseOrderService orders, SupplierService suppliers,
                               MasterDataService masterData, DataScopeService dataScope, IdempotencyService idempotency) {
        this.jdbc = jdbc; this.orders = orders; this.suppliers = suppliers;
        this.masterData = masterData; this.dataScope = dataScope; this.idempotency = idempotency;
    }

    // ==================== 供应商 ====================

    @GetMapping("/suppliers/search")
    @PreAuthorize("hasAuthority('supplier:view')")
    public ApiResponse<List<Map<String, Object>>> supplierSearch(@RequestParam String name) {
        return ApiResponse.ok(jdbc.queryForList(supplierSelect() + " where s.deleted=0 and s.name like ? order by s.score desc limit 100", "%" + name + "%"));
    }

    @GetMapping("/suppliers/page")
    @PreAuthorize("hasAuthority('supplier:view')")
    public ApiResponse<Map<String, Object>> supplierPage(@RequestParam(defaultValue = "1") int current,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String name,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String creditRating) {
        current = Math.max(current, 1);
        size = Math.min(Math.max(size, 1), 100);
        String keyword = "%" + (name == null ? "" : name) + "%";
        Boolean qualified = legacyQualified(status);
        List<Map<String, Object>> records = jdbc.queryForList(supplierSelect() + """
                 where s.deleted=0 and s.name like ?
                   and (? is null or s.level=?)
                   and (? is null or (s.status='QUALIFIED')=?)
                 order by s.id limit ? offset ?
                """, keyword, creditRating, creditRating, qualified, qualified, size, (current - 1) * size);
        Long total = jdbc.queryForObject("""
                select count(*) from sp_supplier s
                where s.deleted=0 and s.name like ? and (? is null or s.level=?) and (? is null or (s.status='QUALIFIED')=?)
                """, Long.class, keyword, creditRating, creditRating, qualified, qualified);
        return ApiResponse.ok(page(records, total, current, size));
    }

    @GetMapping("/suppliers/get/{id}")
    @PreAuthorize("hasAuthority('supplier:view')")
    public ApiResponse<Map<String, Object>> supplierGet(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForMap(supplierSelect() + " where s.id=? and s.deleted=0", id));
    }

    @PostMapping("/suppliers/create")
    @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')")
    public ApiResponse<Map<String, Object>> supplierCreate(@RequestBody Map<String, Object> body) {
        String code = required(body, "supplierCode");
        String name = required(body, "name");
        SupplierService.SupplierRequest request = new SupplierService.SupplierRequest(
                code, name, text(body, "unifiedCreditCode", null), text(body, "creditRating", "B"), "PENDING",
                text(body, "contactPerson", null), text(body, "phone", null), text(body, "email", null), text(body, "address", null));
        Long id = suppliers.createSupplier(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(id));
        result.put("supplierCode", code);
        result.put("lifecycleStatus", "PENDING");
        result.put("note", "新供应商需先提交有效资质，审核通过后才能转为合格供应商");
        return ApiResponse.ok(result);
    }

    @PatchMapping("/suppliers/update-status/{id}")
    @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')")
    public ApiResponse<Void> supplierStatus(@PathVariable Long id, @RequestParam String status) {
        suppliers.setSupplierStatus(id, lifecycleStatus(status));
        return ApiResponse.ok(null);
    }

    // ==================== 物资 ====================

    @GetMapping("/parts/get/{id}")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<Map<String, Object>> partGet(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForMap(partSelect() + " where m.id=? and m.deleted=0", id));
    }

    @GetMapping("/parts/search")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<List<Map<String, Object>>> partSearch(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String specification,
            @RequestParam(required = false) String trackingMode) {
        StringBuilder sql = new StringBuilder(partSelect()).append(" where m.deleted=0");
        List<Object> args = new ArrayList<>();
        appendPartFilters(sql, args, name, q, brand, category, specification, trackingMode);
        sql.append(" order by m.id limit 100");
        return ApiResponse.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/parts/page")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<Map<String, Object>> partPage(@RequestParam(defaultValue = "1") int current,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) String name,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String brand,
                                                     @RequestParam(required = false) String category,
                                                     @RequestParam(required = false) String specification,
                                                     @RequestParam(required = false) String trackingMode,
                                                     @RequestParam(required = false) Long supplierId) {
        current = Math.max(current, 1);
        size = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" where m.deleted=0");
        List<Object> args = new ArrayList<>();
        appendPartFilters(where, args, name, q, brand, category, specification, trackingMode);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((current - 1) * size);
        List<Map<String, Object>> records = jdbc.queryForList(
                partSelect() + where + " order by m.id limit ? offset ?", pageArgs.toArray());
        Long total = jdbc.queryForObject(
                "select count(*) from md_material m join md_material_category c on c.id=m.category_id "
                        + "left join md_brand b on b.id=m.brand_id" + where,
                Long.class, args.toArray());
        return ApiResponse.ok(page(records, total, current, size));
    }

    /** 商品模糊检索：编码/名称/品牌/规格/分类；品牌、规格、追踪方式可单独过滤。 */
    private void appendPartFilters(StringBuilder sql, List<Object> args, String name, String q,
                                   String brand, String category, String specification,
                                   String trackingMode) {
        String keyword = (q != null && !q.isBlank()) ? q.trim()
                : (name == null || name.isBlank() ? null : name.trim());
        if (keyword != null) {
            String like = "%" + keyword + "%";
            sql.append(" and (m.code like ? or m.name like ? or b.name like ?"
                    + " or m.specification like ? or c.name like ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (brand != null && !brand.isBlank()) {
            sql.append(" and b.name like ?");
            args.add("%" + brand.trim() + "%");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and c.name like ?");
            args.add("%" + category.trim() + "%");
        }
        if (specification != null && !specification.isBlank()) {
            sql.append(" and m.specification like ?");
            args.add("%" + specification.trim() + "%");
        }
        if (trackingMode != null && !trackingMode.isBlank()) {
            sql.append(" and m.tracking_mode = ?");
            args.add(trackingMode.trim().toUpperCase());
        }
    }

    @GetMapping("/parts/supplier/{supplierId}")
    @PreAuthorize("hasAuthority('masterdata:view')")
    public ApiResponse<List<Map<String, Object>>> partsBySupplier(@PathVariable Long supplierId) {
        return ApiResponse.ok(jdbc.queryForList(partSelect() + """
                 where m.deleted=0 and exists(
                   select 1 from po_purchase_order_item i join po_purchase_order o on o.id=i.purchase_order_id
                   where i.material_id=m.id and o.supplier_id=?)
                """, supplierId));
    }

    @PostMapping("/parts/create")
    @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')")
    public ApiResponse<Map<String, Object>> partCreate(@RequestBody Map<String, Object> body) {
        String code = required(body, "partCode");
        String name = required(body, "name");
        Long categoryId = lookup("md_material_category", text(body, "category", null), "物资分类");
        Long unitId = lookup("md_unit", text(body, "unit", null), "计量单位");
        String specification = join(text(body, "model", null), text(body, "specification", null));
        Long id = masterData.createMaterial(new MasterDataService.MaterialRequest(
                code, name, categoryId, unitId, null, specification, "QUANTITY",
                decimal(body, "stockWarningValue"), Map.of()));
        return ApiResponse.ok(Map.of("id", String.valueOf(id), "partCode", code));
    }

    // ==================== 订单 ====================

    @GetMapping("/orders/page")
    @PreAuthorize("hasAuthority('purchaseorder:view')")
    public ApiResponse<Map<String, Object>> orderPage(@RequestParam(defaultValue = "1") int current,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(required = false) String orderNumber,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        current = Math.max(current, 1);
        size = Math.min(Math.max(size, 1), 100);
        String keyword = "%" + (orderNumber == null ? "" : orderNumber) + "%";
        String state = LegacyStatus.filter(status);
        DataScopeService.SqlFilter scope = dataScope.purchaseOrderFilter("o", "u");
        String clause = """
                o.deleted=0 and """ + " " + scope.clause() + """
                 and o.order_no like ? and (? is null or o.status=?)
                 and (? is null or date(o.created_at)>=?) and (? is null or date(o.created_at)<=?)
                """;
        List<Object> args = new ArrayList<>(Arrays.asList(scope.args()));
        args.add(keyword);
        args.add(state); args.add(state);
        args.add(startDate); args.add(startDate);
        args.add(endDate); args.add(endDate);
        Long total = jdbc.queryForObject("select count(*) from po_purchase_order o join sys_user u on u.id=o.purchaser_id where " + clause, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((current - 1) * size);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(o.id as char) id,o.order_no orderNumber,o.total_amount totalAmount,o.status,o.created_at orderTime,
                       s.name supplierName,u.display_name purchaser
                from po_purchase_order o
                join sp_supplier s on s.id=o.supplier_id
                join sys_user u on u.id=o.purchaser_id
                where """ + " " + clause + " order by o.id desc limit ? offset ?", pageArgs.toArray());
        return ApiResponse.ok(page(records, total, current, size));
    }

    @GetMapping("/orders/get/{id}")
    @PreAuthorize("hasAuthority('purchaseorder:view')")
    public ApiResponse<Map<String, Object>> orderGet(@PathVariable Long id) {
        Map<String, Object> detail = orders.detail(id);
        detail.put("orderDetail", detail.get("items"));
        return ApiResponse.ok(detail);
    }

    @GetMapping("/orders/search-details")
    @PreAuthorize("hasAuthority('purchaseorder:view')")
    public ApiResponse<List<Map<String, Object>>> orderDetails(@RequestParam(required = false) String partName,
                                                               @RequestParam(required = false) String startDate,
                                                               @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(jdbc.queryForList("""
                select o.order_no orderNumber,m.code partCode,m.name partName,i.quantity,i.unit_price unitPrice,
                       s.name supplierName,o.created_at orderTime
                from po_purchase_order_item i
                join po_purchase_order o on o.id=i.purchase_order_id
                join md_material m on m.id=i.material_id
                join sp_supplier s on s.id=o.supplier_id
                where i.deleted=0 and m.name like ?
                  and (? is null or date(o.created_at)>=?) and (? is null or date(o.created_at)<=?)
                order by o.created_at desc
                """, "%" + (partName == null ? "" : partName) + "%", startDate, startDate, endDate, endDate));
    }

    @GetMapping("/orders/statistics")
    @PreAuthorize("hasAuthority('purchaseorder:view')")
    public ApiResponse<Map<String, Object>> orderStatistics(@RequestParam(required = false) String startDate,
                                                            @RequestParam(required = false) String endDate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCount", jdbc.queryForObject("select count(*) from po_purchase_order where deleted=0 and (? is null or date(created_at)>=?) and (? is null or date(created_at)<=?)", Long.class, startDate, startDate, endDate, endDate));
        data.put("totalAmount", jdbc.queryForObject("select coalesce(sum(total_amount),0) from po_purchase_order where deleted=0 and (? is null or date(created_at)>=?) and (? is null or date(created_at)<=?)", BigDecimal.class, startDate, startDate, endDate, endDate));
        data.put("statusCounts", jdbc.queryForList("select status,count(*) count from po_purchase_order where deleted=0 group by status"));
        return ApiResponse.ok(data);
    }

    @PostMapping("/orders/create")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Map<String, Object>> orderCreate(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                        @RequestBody Map<String, Object> body) {
        PurchaseOrderService.DirectOrderRequest request = directOrder(body);
        Map<String, Object> result = idempotency.execute(key, "LEGACY_CREATE_ORDER", request, () -> orders.createDirect(request));
        return ApiResponse.ok(result);
    }

    @PutMapping("/orders/update/{id}")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Void> orderUpdate(@PathVariable Long id,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                         @RequestBody Map<String, Object> body) {
        PurchaseOrderService.DirectOrderRequest request = directOrder(body);
        idempotency.execute(key, "LEGACY_UPDATE_ORDER", Map.of("id", id, "request", request), () -> {
            orders.updateDirect(id, request);
            return Map.of("id", String.valueOf(id), "updated", true);
        });
        return ApiResponse.ok(null);
    }

    @PatchMapping("/orders/update-status/{id}")
    @PreAuthorize("hasAuthority('purchaseorder:edit')")
    public ApiResponse<Void> orderStatus(@PathVariable Long id, @RequestParam String status) {
        switch (LegacyStatus.decision(status)) {
            case SUBMIT -> orders.submit(id);
            case CLOSE -> orders.close(id);
        }
        return ApiResponse.ok(null);
    }

    // ==================== 库存查询 ====================

    @GetMapping("/inventory/warning")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<List<Map<String, Object>>> inventoryWarning() {
        return ApiResponse.ok(jdbc.queryForList(inventorySelect() + " where b.quantity<m.min_stock order by b.id limit 1000"));
    }

    @GetMapping("/inventory/page")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> inventoryPage(@RequestParam(defaultValue = "1") int current,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String partName,
                                                          @RequestParam(required = false) String warehouseLocation) {
        current = Math.max(current, 1);
        size = Math.min(Math.max(size, 1), 100);
        String partKeyword = "%" + (partName == null ? "" : partName) + "%";
        String warehouseKeyword = "%" + (warehouseLocation == null ? "" : warehouseLocation) + "%";
        List<Map<String, Object>> records = jdbc.queryForList(inventorySelect() + """
                 where m.name like ? and (w.name like ? or w.address like ?)
                 order by b.id limit ? offset ?
                """, partKeyword, warehouseKeyword, warehouseKeyword, size, (current - 1) * size);
        Long total = jdbc.queryForObject("""
                select count(*) from inv_balance b
                join md_material m on m.id=b.material_id join md_warehouse w on w.id=b.warehouse_id
                where m.name like ? and (w.name like ? or w.address like ?)
                """, Long.class, partKeyword, warehouseKeyword, warehouseKeyword);
        return ApiResponse.ok(page(records, total, current, size));
    }

    @GetMapping("/inventory/check")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> inventoryCheck() {
        return ApiResponse.ok(Map.of(
                "totalSku", jdbc.queryForObject("select count(*) from inv_balance where deleted=0", Long.class),
                "warningCount", jdbc.queryForObject("select count(*) from inv_balance b join md_material m on m.id=b.material_id where b.deleted=0 and b.quantity<m.min_stock", Long.class),
                "totalQuantity", jdbc.queryForObject("select coalesce(sum(quantity),0) from inv_balance where deleted=0", BigDecimal.class)));
    }

    @GetMapping("/inventory/get/{id}")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<Map<String, Object>> inventoryGet(@PathVariable Long id) {
        return ApiResponse.ok(jdbc.queryForMap(inventorySelect() + " where b.id=?", id));
    }

    @PostMapping({"/inventory/inbound", "/inventory/outbound"})
    @PreAuthorize("hasAuthority('inventory:receive')")
    public ApiResponse<Void> legacyInventoryWrite() {
        throw new BusinessException(HttpStatus.CONFLICT, "库存变动必须通过已审批订单的正式收货接口（/api/v1/receipts），Agent不能直接调整库存");
    }

    // ==================== 内部实现 ====================

    private String supplierSelect() {
        return """
                select cast(s.id as char) id,s.code supplierCode,s.name,s.unified_credit_code unifiedCreditCode,
                       s.contact_name contactPerson,s.contact_phone phone,s.email,s.address,
                       s.level creditRating,s.status lifecycleStatus,(s.status='QUALIFIED') status,s.score
                from sp_supplier s
                """;
    }

    private String partSelect() {
        return """
                select cast(m.id as char) id,m.code partCode,m.name,m.specification model,m.specification,
                       u.name unit,m.min_stock stockWarningValue,c.name category,m.tracking_mode trackingMode,
                       b.name brand,m.enabled,(m.enabled=1) available,m.attributes_json attributes
                from md_material m
                join md_material_category c on c.id=m.category_id
                join md_unit u on u.id=m.unit_id
                left join md_brand b on b.id=m.brand_id
                """;
    }

    private String inventorySelect() {
        return """
                select cast(b.id as char) id,b.quantity currentQuantity,m.min_stock safetyStock,
                       w.name warehouseLocation,m.code partCode,m.name partName,m.tracking_mode trackingMode
                from inv_balance b
                join md_material m on m.id=b.material_id
                join md_warehouse w on w.id=b.warehouse_id
                """;
    }

    private Map<String, Object> page(List<Map<String, Object>> records, Long total, int current, int size) {
        int totalCount = total == null ? 0 : total.intValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", totalCount);
        result.put("current", current);
        result.put("size", size);
        result.put("pages", (totalCount + size - 1) / size);
        return result;
    }

    private PurchaseOrderService.DirectOrderRequest directOrder(Map<String, Object> body) {
        List<PurchaseOrderService.DirectItem> items = new ArrayList<>();
        Object raw = body.get("orderDetail");
        if (raw == null) raw = body.get("items");
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> map)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((k, v) -> item.put(String.valueOf(k), v));
                items.add(new PurchaseOrderService.DirectItem(
                        longValue(item, "partId"), decimal(item, "quantity"),
                        decimal(item, "unitPrice"), text(item, "remark", null)));
            }
        }
        return new PurchaseOrderService.DirectOrderRequest(
                longValue(body, "supplierId"), date(body, "deliveryDate"), decimal(body, "taxRate"),
                text(body, "terms", text(body, "remark", null)), items);
    }

    private Boolean legacyQualified(String status) {
        if (status == null || status.isBlank()) return null;
        String value = status.trim().toUpperCase();
        if (List.of("1", "QUALIFIED", "TRUE").contains(value)) return true;
        if (List.of("0", "SUSPENDED", "FALSE").contains(value)) return false;
        throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的供应商状态：" + status);
    }

    private String lifecycleStatus(String status) {
        if (status == null || status.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "供应商状态不能为空");
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "1", "QUALIFIED" -> "QUALIFIED";
            case "0", "SUSPENDED" -> "SUSPENDED";
            case "PENDING", "BLACKLISTED" -> value;
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的供应商状态：" + status);
        };
    }

    private Long lookup(String table, String value, String label) {
        if (value == null || value.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, label + "不能为空");
        List<Long> ids = jdbc.queryForList("select id from " + table + " where (code=? or name=?) and deleted=0 limit 1", Long.class, value.trim(), value.trim());
        if (ids.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, label + "不存在：" + value);
        return ids.getFirst();
    }

    private String required(Map<String, Object> body, String key) {
        String value = String.valueOf(body.getOrDefault(key, "")).trim();
        if (value.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, key + "不能为空");
        return value;
    }

    private String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private Long longValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { throw new BusinessException(HttpStatus.BAD_REQUEST, key + " 必须是整数"); }
    }

    private BigDecimal decimal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { throw new BusinessException(HttpStatus.BAD_REQUEST, key + " 必须是数字"); }
    }

    private LocalDate date(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return LocalDate.parse(String.valueOf(value).trim()); }
        catch (Exception e) { throw new BusinessException(HttpStatus.BAD_REQUEST, key + " 必须是 yyyy-MM-dd 格式"); }
    }

    private String join(String first, String second) {
        List<String> parts = new ArrayList<>();
        if (first != null && !first.isBlank()) parts.add(first.trim());
        if (second != null && !second.isBlank()) parts.add(second.trim());
        return parts.isEmpty() ? null : String.join(" ", parts);
    }
}
