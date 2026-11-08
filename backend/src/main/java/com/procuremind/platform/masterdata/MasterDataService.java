package com.procuremind.platform.masterdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MasterDataService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MasterDataService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(c.id as char) id,cast(c.parent_id as char) parentId,c.code,c.name,c.description,c.sort_order sortOrder,
                       (select count(*) from md_material m where m.category_id=c.id and m.deleted=0) materialCount
                from md_material_category c where c.deleted=0 order by c.sort_order,c.id
                """);
        rows.forEach(row -> row.put("attributes", attributes(Long.valueOf(row.get("id").toString()))));
        return rows;
    }

    public Map<String, Object> category(Long id) {
        return categories().stream().filter(row -> id.toString().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> notFound("物资分类不存在"));
    }

    @Transactional
    public Long createCategory(CategoryRequest request) {
        validateCategoryRequest(null, request);
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into md_material_category(parent_id,code,name,description,sort_order,created_by,updated_by) values(?,?,?,?,?,?,?)",
                request.parentId(), request.code(), request.name(), request.description(), defaultSort(request.sortOrder()), operator, operator);
        Long id = jdbc.queryForObject("select id from md_material_category where code=?", Long.class, request.code());
        replaceAttributes(id, request.attributes());
        return id;
    }

    @Transactional
    public void updateCategory(Long id, CategoryRequest request) {
        requireCategory(id);
        validateCategoryRequest(id, request);
        validateExistingMaterialAttributes(id, request.attributes());
        jdbc.update("update md_material_category set parent_id=?,code=?,name=?,description=?,sort_order=?,updated_by=? where id=? and deleted=0",
                request.parentId(), request.code(), request.name(), request.description(), defaultSort(request.sortOrder()), SecurityUtils.currentUser().id(), id);
        replaceAttributes(id, request.attributes());
    }

    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);
        if (count("select count(*) from md_material_category where parent_id=? and deleted=0", id) > 0) throw conflict("分类存在下级分类，不能删除");
        if (count("select count(*) from md_material where category_id=? and deleted=0", id) > 0) throw conflict("分类已关联物资，不能删除");
        jdbc.update("delete from md_category_attribute where category_id=?", id);
        jdbc.update("update md_material_category set deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    public List<Map<String, Object>> materials(String keyword) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbc.queryForList("""
                select cast(m.id as char) id,m.code,m.name,cast(m.category_id as char) categoryId,c.name category,
                       cast(m.unit_id as char) unitId,u.name unit,cast(m.brand_id as char) brandId,b.name brand,
                       m.specification,m.tracking_mode trackingMode,m.min_stock minStock,m.attributes_json attributes,m.enabled
                from md_material m join md_material_category c on c.id=m.category_id
                join md_unit u on u.id=m.unit_id left join md_brand b on b.id=m.brand_id
                where m.deleted=0 and (m.code like ? or m.name like ?) order by m.id desc
                """, like, like);
    }

    public Map<String, Object> materialsPage(String keyword, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        Long total = jdbc.queryForObject("select count(*) from md_material m where m.deleted=0 and (m.code like ? or m.name like ?)", Long.class, like, like);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(m.id as char) id,m.code,m.name,cast(m.category_id as char) categoryId,c.name category,
                       cast(m.unit_id as char) unitId,u.name unit,cast(m.brand_id as char) brandId,b.name brand,
                       m.specification,m.tracking_mode trackingMode,m.min_stock minStock,m.attributes_json attributes,m.enabled
                from md_material m join md_material_category c on c.id=m.category_id
                join md_unit u on u.id=m.unit_id left join md_brand b on b.id=m.brand_id
                where m.deleted=0 and (m.code like ? or m.name like ?)
                order by m.id desc limit ? offset ?
                """, like, like, pageSize, (page - 1) * pageSize);
        long totalCount = total == null ? 0 : total;
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) totalCount);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((totalCount + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> material(Long id) {
        return materials("").stream().filter(row -> id.toString().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> notFound("物资不存在"));
    }

    @Transactional
    public Long createMaterial(MaterialRequest request) {
        validateMaterialRequest(null, request);
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("""
                insert into md_material(code,name,category_id,unit_id,brand_id,specification,tracking_mode,min_stock,attributes_json,created_by,updated_by)
                values(?,?,?,?,?,?,?,?,?,?,?)
                """, request.code(), request.name(), request.categoryId(), request.unitId(), request.brandId(), request.specification(),
                request.trackingMode(), defaultStock(request.minStock()), json(request.attributes()), operator, operator);
        return jdbc.queryForObject("select id from md_material where code=?", Long.class, request.code());
    }

    @Transactional
    public void updateMaterial(Long id, MaterialRequest request) {
        Map<String, Object> existing = material(id);
        if (!existing.get("code").toString().equals(request.code())) throw conflict("物资编码创建后不允许修改");
        if (!existing.get("trackingMode").toString().equals(request.trackingMode()) && hasInventoryHistory(id))
            throw conflict("物资已有库存记录，不能修改库存跟踪方式");
        validateMaterialRequest(id, request);
        jdbc.update("""
                update md_material set name=?,category_id=?,unit_id=?,brand_id=?,specification=?,tracking_mode=?,min_stock=?,attributes_json=?,updated_by=?
                where id=? and deleted=0
                """, request.name(), request.categoryId(), request.unitId(), request.brandId(), request.specification(), request.trackingMode(),
                defaultStock(request.minStock()), json(request.attributes()), SecurityUtils.currentUser().id(), id);
    }

    public void setMaterialStatus(Long id, boolean enabled) {
        requireMaterial(id);
        jdbc.update("update md_material set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void deleteMaterial(Long id) {
        requireMaterial(id);
        if (count("select count(*) from pr_requisition_item where material_id=? and deleted=0", id) > 0
                || count("select count(*) from po_purchase_order_item where material_id=? and deleted=0", id) > 0
                || count("select count(*) from inv_receipt_item where material_id=? and deleted=0", id) > 0
                || count("select count(*) from inv_balance where material_id=? and deleted=0", id) > 0
                || count("select count(*) from inv_ledger where material_id=?", id) > 0)
            throw conflict("物资已有采购或库存记录，只能停用");
        jdbc.update("update md_material set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    public List<Map<String, Object>> warehouses() {
        return jdbc.queryForList("""
                select cast(w.id as char) id,w.code,w.name,w.address,w.manager,w.enabled,
                       (select count(*) from inv_balance b where b.warehouse_id=w.id and b.deleted=0) balanceCount,
                       (select count(*) from inv_receipt r where r.warehouse_id=w.id and r.deleted=0) receiptCount
                from md_warehouse w where w.deleted=0 order by w.id
                """);
    }

    public Map<String, Object> warehouse(Long id) {
        return warehouses().stream().filter(row -> id.toString().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> notFound("仓库不存在"));
    }

    @Transactional
    public Long createWarehouse(WarehouseRequest request) {
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into md_warehouse(code,name,address,manager,enabled,created_by,updated_by) values(?,?,?,?,1,?,?)",
                request.code(), request.name(), request.address(), request.manager(), operator, operator);
        return jdbc.queryForObject("select id from md_warehouse where code=?", Long.class, request.code());
    }

    public void updateWarehouse(Long id, WarehouseRequest request) {
        requireWarehouse(id, false);
        jdbc.update("update md_warehouse set code=?,name=?,address=?,manager=?,updated_by=? where id=? and deleted=0",
                request.code(), request.name(), request.address(), request.manager(), SecurityUtils.currentUser().id(), id);
    }

    public void setWarehouseStatus(Long id, boolean enabled) {
        requireWarehouse(id, false);
        if (!enabled && count("select count(*) from inv_balance where warehouse_id=? and deleted=0 and quantity<>0", id) > 0)
            throw conflict("仓库仍有库存，不能停用");
        jdbc.update("update md_warehouse set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
    }

    public void deleteWarehouse(Long id) {
        requireWarehouse(id, false);
        if (count("select count(*) from inv_receipt where warehouse_id=? and deleted=0", id) > 0
                || count("select count(*) from inv_balance where warehouse_id=? and deleted=0", id) > 0
                || count("select count(*) from inv_ledger where warehouse_id=?", id) > 0
                || count("select count(*) from inv_batch where warehouse_id=?", id) > 0
                || count("select count(*) from inv_serial where warehouse_id=?", id) > 0)
            throw conflict("仓库已有收货或库存记录，只能停用");
        jdbc.update("update md_warehouse set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    public List<Map<String, Object>> units() {
        return jdbc.queryForList("""
                select cast(u.id as char) id,u.code,u.name,u.precision_scale precisionScale,u.enabled,
                       (select count(*) from md_material m where m.unit_id=u.id and m.deleted=0) materialCount
                from md_unit u where u.deleted=0 order by u.id
                """);
    }

    public Map<String, Object> unit(Long id) {
        return units().stream().filter(row -> id.toString().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> notFound("计量单位不存在"));
    }

    @Transactional
    public Long createUnit(UnitRequest request) {
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into md_unit(code,name,precision_scale,enabled,created_by,updated_by) values(?,?,?,1,?,?)",
                request.code(), request.name(), request.precisionScale(), operator, operator);
        return jdbc.queryForObject("select id from md_unit where code=?", Long.class, request.code());
    }

    public void updateUnit(Long id, UnitRequest request) {
        requireUnit(id, false);
        if (count("select count(*) from md_material m join po_purchase_order_item i on i.material_id=m.id and i.deleted=0 where m.unit_id=? and m.deleted=0", id) > 0) {
            Integer current = jdbc.queryForObject("select precision_scale from md_unit where id=?", Integer.class, id);
            if (current != null && !current.equals(request.precisionScale())) throw conflict("计量单位已用于采购订单，不能修改小数精度");
        }
        jdbc.update("update md_unit set code=?,name=?,precision_scale=?,updated_by=? where id=? and deleted=0",
                request.code(), request.name(), request.precisionScale(), SecurityUtils.currentUser().id(), id);
    }

    public void setUnitStatus(Long id, boolean enabled) {
        requireUnit(id, false);
        jdbc.update("update md_unit set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
    }

    public void deleteUnit(Long id) {
        requireUnit(id, false);
        if (count("select count(*) from md_material where unit_id=? and deleted=0", id) > 0) throw conflict("计量单位已被物资引用，只能停用");
        jdbc.update("update md_unit set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    public List<Map<String, Object>> brands() {
        return jdbc.queryForList("""
                select cast(b.id as char) id,b.code,b.name,b.enabled,
                       (select count(*) from md_material m where m.brand_id=b.id and m.deleted=0) materialCount
                from md_brand b where b.deleted=0 order by b.id
                """);
    }

    public Map<String, Object> brand(Long id) {
        return brands().stream().filter(row -> id.toString().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> notFound("品牌不存在"));
    }

    @Transactional
    public Long createBrand(BrandRequest request) {
        Long operator = SecurityUtils.currentUser().id();
        jdbc.update("insert into md_brand(code,name,enabled,created_by,updated_by) values(?,?,1,?,?)",
                request.code(), request.name(), operator, operator);
        return jdbc.queryForObject("select id from md_brand where code=?", Long.class, request.code());
    }

    public void updateBrand(Long id, BrandRequest request) {
        requireBrand(id, false);
        jdbc.update("update md_brand set code=?,name=?,updated_by=? where id=? and deleted=0",
                request.code(), request.name(), SecurityUtils.currentUser().id(), id);
    }

    public void setBrandStatus(Long id, boolean enabled) {
        requireBrand(id, false);
        jdbc.update("update md_brand set enabled=?,updated_by=? where id=? and deleted=0", enabled, SecurityUtils.currentUser().id(), id);
    }

    public void deleteBrand(Long id) {
        requireBrand(id, false);
        if (count("select count(*) from md_material where brand_id=? and deleted=0", id) > 0) throw conflict("品牌已被物资引用，只能停用");
        jdbc.update("update md_brand set enabled=0,deleted=1,updated_by=? where id=?", SecurityUtils.currentUser().id(), id);
    }

    private void validateCategoryRequest(Long id, CategoryRequest request) {
        validateAttributeDefinitions(request.attributes());
        if (request.parentId() != null) {
            requireCategory(request.parentId());
            if (id != null) ensureNoCategoryCycle(id, request.parentId());
        }
    }

    private void validateAttributeDefinitions(List<AttributeRequest> definitions) {
        if (definitions == null) return;
        Set<String> codes = new HashSet<>();
        for (AttributeRequest definition : definitions) {
            if (!codes.add(definition.code())) throw new BusinessException(HttpStatus.BAD_REQUEST, "动态属性编码不能重复");
            if ("ENUM".equals(definition.valueType()) && (definition.options() == null || definition.options().isEmpty()))
                throw new BusinessException(HttpStatus.BAD_REQUEST, definition.name() + "必须配置枚举选项");
            if (definition.options() != null && definition.options().size() != new HashSet<>(definition.options()).size())
                throw new BusinessException(HttpStatus.BAD_REQUEST, definition.name() + "的枚举选项不能重复");
        }
    }

    private void validateExistingMaterialAttributes(Long categoryId, List<AttributeRequest> definitions) {
        Map<String, AttributeRequest> next = new LinkedHashMap<>();
        if (definitions != null) definitions.forEach(definition -> next.put(definition.code(), definition));
        Set<String> previousCodes = new HashSet<>();
        attributes(categoryId).forEach(attribute -> previousCodes.add(attribute.get("code").toString()));
        List<Map<String, Object>> values = jdbc.queryForList("select code,attributes_json attributes from md_material where category_id=? and deleted=0", categoryId);
        for (Map<String, Object> row : values) {
            Map<String, Object> materialValues = objectMap(row.get("attributes"));
            for (String removed : previousCodes) {
                if (!next.containsKey(removed) && materialValues.get(removed) != null)
                    throw conflict("动态属性“" + removed + "”已有物资数据，不能删除");
            }
            try {
                validateAttributes(next, materialValues);
            } catch (BusinessException e) {
                throw conflict("分类属性定义与物资“" + row.get("code") + "”的现有数据不兼容：" + e.getMessage());
            }
        }
    }

    private void validateMaterialRequest(Long id, MaterialRequest request) {
        requireCategory(request.categoryId());
        requireUnit(request.unitId(), true);
        if (request.brandId() != null) requireBrand(request.brandId(), true);
        Map<String, AttributeRequest> definitions = new LinkedHashMap<>();
        for (Map<String, Object> row : attributes(request.categoryId())) {
            definitions.put(row.get("code").toString(), new AttributeRequest(row.get("code").toString(), row.get("name").toString(),
                    row.get("valueType").toString(), booleanValue(row.get("required")), parseOptions(row.get("options")),
                    row.get("sortOrder") == null ? 0 : Integer.valueOf(row.get("sortOrder").toString())));
        }
        validateAttributes(definitions, request.attributes() == null ? Map.of() : request.attributes());
    }

    private void validateAttributes(Map<String, AttributeRequest> definitions, Map<String, Object> values) {
        for (String code : values.keySet()) if (!definitions.containsKey(code)) throw new BusinessException(HttpStatus.BAD_REQUEST, "存在未定义的动态属性：" + code);
        for (AttributeRequest definition : definitions.values()) {
            Object value = values.get(definition.code());
            if (Boolean.TRUE.equals(definition.required()) && (value == null || value.toString().isBlank()))
                throw new BusinessException(HttpStatus.BAD_REQUEST, definition.name() + "不能为空");
            if (value != null) validateAttributeValue(definition, value);
        }
    }

    private void validateAttributeValue(AttributeRequest definition, Object value) {
        try {
            switch (definition.valueType()) {
                case "NUMBER" -> new BigDecimal(value.toString());
                case "BOOLEAN" -> {
                    if (!Set.of("true", "false").contains(value.toString().toLowerCase())) throw new IllegalArgumentException();
                }
                case "DATE" -> LocalDate.parse(value.toString());
                case "ENUM" -> {
                    if (definition.options() == null || !definition.options().contains(value.toString())) throw new IllegalArgumentException();
                }
                default -> { }
            }
        } catch (Exception ignored) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, definition.name() + "的值与属性类型不匹配");
        }
    }

    private List<Map<String, Object>> attributes(Long categoryId) {
        return jdbc.queryForList("""
                select cast(id as char) id,code,name,value_type valueType,required,options_json options,sort_order sortOrder
                from md_category_attribute where category_id=? order by sort_order,id
                """, categoryId);
    }

    private void replaceAttributes(Long categoryId, List<AttributeRequest> definitions) {
        jdbc.update("delete from md_category_attribute where category_id=?", categoryId);
        if (definitions == null) return;
        for (AttributeRequest definition : definitions) {
            jdbc.update("""
                    insert into md_category_attribute(category_id,code,name,value_type,required,options_json,sort_order)
                    values(?,?,?,?,?,?,?)
                    """, categoryId, definition.code(), definition.name(), definition.valueType(), Boolean.TRUE.equals(definition.required()),
                    json(definition.options()), defaultSort(definition.sortOrder()));
        }
    }

    private void ensureNoCategoryCycle(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (current.equals(id) || !visited.add(current)) throw conflict("上级分类设置会形成循环");
            List<Long> parents = jdbc.query("select parent_id from md_material_category where id=? and deleted=0", (rs, n) -> {
                long value = rs.getLong(1);
                return rs.wasNull() ? null : value;
            }, current);
            current = parents.isEmpty() ? null : parents.getFirst();
        }
    }

    private boolean hasInventoryHistory(Long materialId) {
        return count("select count(*) from inv_ledger where material_id=?", materialId) > 0
                || count("select count(*) from inv_receipt_item where material_id=? and deleted=0", materialId) > 0
                || count("select count(*) from inv_balance where material_id=? and deleted=0", materialId) > 0;
    }

    private void requireCategory(Long id) {
        if (count("select count(*) from md_material_category where id=? and deleted=0", id) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "物资分类不存在");
    }

    private void requireMaterial(Long id) {
        if (count("select count(*) from md_material where id=? and deleted=0", id) == 0) throw notFound("物资不存在");
    }

    private void requireWarehouse(Long id, boolean enabled) {
        if (count("select count(*) from md_warehouse where id=? and deleted=0" + (enabled ? " and enabled=1" : ""), id) == 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仓库不存在或不可用");
    }

    private void requireUnit(Long id, boolean enabled) {
        if (count("select count(*) from md_unit where id=? and deleted=0" + (enabled ? " and enabled=1" : ""), id) == 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "计量单位不存在或不可用");
    }

    private void requireBrand(Long id, boolean enabled) {
        if (count("select count(*) from md_brand where id=? and deleted=0" + (enabled ? " and enabled=1" : ""), id) == 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "品牌不存在或不可用");
    }

    private Map<String, Object> objectMap(Object value) {
        if (value == null) return Map.of();
        try {
            if (value instanceof String string) return mapper.readValue(string, new TypeReference<>() {});
            return mapper.convertValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.CONFLICT, "已有物资动态属性数据无法解析");
        }
    }

    private List<String> parseOptions(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof String string) return mapper.readValue(string, new TypeReference<>() {});
            return mapper.convertValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.CONFLICT, "动态属性枚举定义无法解析");
        }
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(HttpStatus.BAD_REQUEST, "JSON数据格式错误"); }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean b ? b : value instanceof Number n ? n.intValue() != 0 : Boolean.parseBoolean(String.valueOf(value));
    }

    private int defaultSort(Integer value) { return value == null ? 0 : value; }
    private BigDecimal defaultStock(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }
    private BusinessException notFound(String message) { return new BusinessException(HttpStatus.NOT_FOUND, message); }

    public record CategoryRequest(
            @Positive Long parentId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "分类编码需为大写字母开头") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(0) Integer sortOrder,
            @Size(max = 30) List<@Valid AttributeRequest> attributes) {}

    public record AttributeRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}", message = "属性编码格式不正确") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "TEXT|NUMBER|BOOLEAN|DATE|ENUM") String valueType,
            Boolean required,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> options,
            @Min(0) Integer sortOrder) {}

    public record MaterialRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{1,49}", message = "物资编码格式不正确") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Positive Long categoryId,
            @NotNull @Positive Long unitId,
            @Positive Long brandId,
            @Size(max = 500) String specification,
            @NotBlank @Pattern(regexp = "QUANTITY|BATCH|SERIAL") String trackingMode,
            @DecimalMin("0") BigDecimal minStock,
            @Size(max = 30) Map<String, Object> attributes) {}

    public record WarehouseRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "仓库编码格式不正确") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String address,
            @Size(max = 100) String manager) {}

    public record UnitRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{0,29}", message = "单位编码格式不正确") String code,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Min(0) @Max(6) Integer precisionScale) {}

    public record BrandRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "品牌编码格式不正确") String code,
            @NotBlank @Size(max = 100) String name) {}

    public record StatusRequest(@NotNull Boolean enabled) {}
}
