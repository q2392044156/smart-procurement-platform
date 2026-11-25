package com.procuremind.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.MasterDataMapper;
import com.procuremind.platform.utils.SecurityUtils;
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
    private final MasterDataMapper mapper;
    private final ObjectMapper objectMapper;

    public MasterDataService(MasterDataMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> rows = mapper.listCategories();
        rows.forEach(row -> row.put("attributes", attributes(Long.valueOf(row.get("id").toString()))));
        return rows;
    }

    public Map<String, Object> category(Long id) {
        Map<String, Object> row = findOne(mapper.findCategory(id), "物资分类不存在");
        row.put("attributes", attributes(id));
        return row;
    }

    @Transactional
    public Long createCategory(CategoryRequest request) {
        validateCategoryRequest(null, request);
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertCategory(request.parentId(), request.code(), request.name(), request.description(),
                defaultSort(request.sortOrder()), operator);
        Long id = mapper.findCategoryIdByCode(request.code());
        replaceAttributes(id, request.attributes());
        return id;
    }

    @Transactional
    public void updateCategory(Long id, CategoryRequest request) {
        requireCategory(id);
        validateCategoryRequest(id, request);
        validateExistingMaterialAttributes(id, request.attributes());
        mapper.updateCategory(id, request.parentId(), request.code(), request.name(), request.description(),
                defaultSort(request.sortOrder()), SecurityUtils.currentUser().id());
        replaceAttributes(id, request.attributes());
    }

    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);
        if (mapper.countCategoryChildren(id) > 0) throw conflict("分类存在下级分类，不能删除");
        if (mapper.countMaterialByCategory(id) > 0) throw conflict("分类已关联物资，不能删除");
        mapper.deleteCategoryAttributes(id);
        mapper.softDeleteCategory(id, SecurityUtils.currentUser().id());
    }

    public List<Map<String, Object>> materials(String keyword) {
        return materials(keyword, null, null, null, null);
    }

    public List<Map<String, Object>> materials(String keyword, String brand, String category,
                                               String specification, String trackingMode) {
        return mapper.listMaterials(filterValue(keyword), filterValue(brand), filterValue(category),
                filterValue(specification), trackingModeValue(trackingMode));
    }

    public Map<String, Object> materialsPage(String keyword, int current, int size) {
        return materialsPage(keyword, null, null, null, null, current, size);
    }

    public Map<String, Object> materialsPage(String keyword, String brand, String category,
                                             String specification, String trackingMode, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = filterValue(keyword);
        String normalizedBrand = filterValue(brand);
        String normalizedCategory = filterValue(category);
        String normalizedSpecification = filterValue(specification);
        String normalizedTrackingMode = trackingModeValue(trackingMode);
        long total = mapper.countMaterialsPage(normalizedKeyword, normalizedBrand, normalizedCategory,
                normalizedSpecification, normalizedTrackingMode);
        List<Map<String, Object>> records = mapper.pageMaterials(normalizedKeyword, normalizedBrand,
                normalizedCategory, normalizedSpecification, normalizedTrackingMode, pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) total);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((total + pageSize - 1) / pageSize));
        return result;
    }

    /** 空白查询条件按“未传”处理，与原先 appendMaterialFilters 里 isBlank 跳过等价。 */
    private String filterValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 追踪方式原来就是 trim 后转大写再匹配。 */
    private String trackingModeValue(String value) {
        String normalized = filterValue(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    public Map<String, Object> material(Long id) {
        return findOne(mapper.findMaterial(id), "物资不存在");
    }

    @Transactional
    public Long createMaterial(MaterialRequest request) {
        validateMaterialRequest(null, request);
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertMaterial(request.code(), request.name(), request.categoryId(), request.unitId(), request.brandId(),
                request.specification(), request.trackingMode(), defaultStock(request.minStock()),
                json(request.attributes()), operator);
        return mapper.findMaterialIdByCode(request.code());
    }

    @Transactional
    public void updateMaterial(Long id, MaterialRequest request) {
        Map<String, Object> existing = material(id);
        if (!existing.get("code").toString().equals(request.code())) throw conflict("物资编码创建后不允许修改");
        if (!existing.get("trackingMode").toString().equals(request.trackingMode()) && hasInventoryHistory(id))
            throw conflict("物资已有库存记录，不能修改库存跟踪方式");
        validateMaterialRequest(id, request);
        mapper.updateMaterial(id, request.name(), request.categoryId(), request.unitId(), request.brandId(),
                request.specification(), request.trackingMode(), defaultStock(request.minStock()),
                json(request.attributes()), SecurityUtils.currentUser().id());
    }

    public void setMaterialStatus(Long id, boolean enabled) {
        requireMaterial(id);
        mapper.updateMaterialStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void deleteMaterial(Long id) {
        requireMaterial(id);
        if (mapper.countRequisitionItemByMaterial(id) > 0
                || mapper.countPurchaseOrderItemByMaterial(id) > 0
                || mapper.countReceiptItemByMaterial(id) > 0
                || mapper.countBalanceByMaterial(id) > 0
                || mapper.countLedgerByMaterial(id) > 0)
            throw conflict("物资已有采购或库存记录，只能停用");
        mapper.softDeleteMaterial(id, SecurityUtils.currentUser().id());
    }

    public List<Map<String, Object>> warehouses() {
        return mapper.listWarehouses();
    }

    public Map<String, Object> warehouse(Long id) {
        return findOne(mapper.findWarehouse(id), "仓库不存在");
    }

    @Transactional
    public Long createWarehouse(WarehouseRequest request) {
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertWarehouse(request.code(), request.name(), request.address(), request.manager(), operator);
        return mapper.findWarehouseIdByCode(request.code());
    }

    public void updateWarehouse(Long id, WarehouseRequest request) {
        requireWarehouse(id, false);
        mapper.updateWarehouse(id, request.code(), request.name(), request.address(), request.manager(),
                SecurityUtils.currentUser().id());
    }

    public void setWarehouseStatus(Long id, boolean enabled) {
        requireWarehouse(id, false);
        if (!enabled && mapper.countBalanceWithStock(id) > 0)
            throw conflict("仓库仍有库存，不能停用");
        mapper.updateWarehouseStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    public void deleteWarehouse(Long id) {
        requireWarehouse(id, false);
        if (mapper.countReceiptByWarehouse(id) > 0
                || mapper.countBalanceByWarehouse(id) > 0
                || mapper.countLedgerByWarehouse(id) > 0
                || mapper.countBatchByWarehouse(id) > 0
                || mapper.countSerialByWarehouse(id) > 0)
            throw conflict("仓库已有收货或库存记录，只能停用");
        mapper.softDeleteWarehouse(id, SecurityUtils.currentUser().id());
    }

    public List<Map<String, Object>> units() {
        return mapper.listUnits();
    }

    public Map<String, Object> unit(Long id) {
        return findOne(mapper.findUnit(id), "计量单位不存在");
    }

    @Transactional
    public Long createUnit(UnitRequest request) {
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertUnit(request.code(), request.name(), request.precisionScale(), operator);
        return mapper.findUnitIdByCode(request.code());
    }

    public void updateUnit(Long id, UnitRequest request) {
        requireUnit(id, false);
        if (mapper.countUnitUsedInPurchaseOrders(id) > 0) {
            Integer current = mapper.findUnitPrecisionScale(id);
            if (current != null && !current.equals(request.precisionScale())) throw conflict("计量单位已用于采购订单，不能修改小数精度");
        }
        mapper.updateUnit(id, request.code(), request.name(), request.precisionScale(), SecurityUtils.currentUser().id());
    }

    public void setUnitStatus(Long id, boolean enabled) {
        requireUnit(id, false);
        mapper.updateUnitStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    public void deleteUnit(Long id) {
        requireUnit(id, false);
        if (mapper.countMaterialByUnit(id) > 0) throw conflict("计量单位已被物资引用，只能停用");
        mapper.softDeleteUnit(id, SecurityUtils.currentUser().id());
    }

    public List<Map<String, Object>> brands() {
        return mapper.listBrands();
    }

    public Map<String, Object> brand(Long id) {
        return findOne(mapper.findBrand(id), "品牌不存在");
    }

    @Transactional
    public Long createBrand(BrandRequest request) {
        Long operator = SecurityUtils.currentUser().id();
        mapper.insertBrand(request.code(), request.name(), operator);
        return mapper.findBrandIdByCode(request.code());
    }

    public void updateBrand(Long id, BrandRequest request) {
        requireBrand(id, false);
        mapper.updateBrand(id, request.code(), request.name(), SecurityUtils.currentUser().id());
    }

    public void setBrandStatus(Long id, boolean enabled) {
        requireBrand(id, false);
        mapper.updateBrandStatus(id, enabled, SecurityUtils.currentUser().id());
    }

    public void deleteBrand(Long id) {
        requireBrand(id, false);
        if (mapper.countMaterialByBrand(id) > 0) throw conflict("品牌已被物资引用，只能停用");
        mapper.softDeleteBrand(id, SecurityUtils.currentUser().id());
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
        List<Map<String, Object>> values = mapper.listMaterialAttributeValues(categoryId);
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
        return mapper.listCategoryAttributes(categoryId);
    }

    private void replaceAttributes(Long categoryId, List<AttributeRequest> definitions) {
        mapper.deleteCategoryAttributes(categoryId);
        if (definitions == null) return;
        for (AttributeRequest definition : definitions) {
            mapper.insertCategoryAttribute(categoryId, definition.code(), definition.name(), definition.valueType(),
                    Boolean.TRUE.equals(definition.required()), json(definition.options()), defaultSort(definition.sortOrder()));
        }
    }

    private void ensureNoCategoryCycle(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (current.equals(id) || !visited.add(current)) throw conflict("上级分类设置会形成循环");
            current = mapper.findParentId(current);
        }
    }

    private boolean hasInventoryHistory(Long materialId) {
        return mapper.countLedgerByMaterial(materialId) > 0
                || mapper.countReceiptItemByMaterial(materialId) > 0
                || mapper.countBalanceByMaterial(materialId) > 0;
    }

    private void requireCategory(Long id) {
        if (mapper.countCategoryById(id) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "物资分类不存在");
    }

    private void requireMaterial(Long id) {
        if (mapper.countMaterialById(id) == 0) throw notFound("物资不存在");
    }

    private void requireWarehouse(Long id, boolean enabled) {
        if (mapper.countWarehouseById(id, enabled) == 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仓库不存在或不可用");
    }

    private void requireUnit(Long id, boolean enabled) {
        if (mapper.countUnitById(id, enabled) == 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "计量单位不存在或不可用");
    }

    private void requireBrand(Long id, boolean enabled) {
        if (mapper.countBrandById(id, enabled) == 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "品牌不存在或不可用");
    }

    private Map<String, Object> objectMap(Object value) {
        if (value == null) return Map.of();
        try {
            if (value instanceof String string) return objectMapper.readValue(string, new TypeReference<>() {});
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.CONFLICT, "已有物资动态属性数据无法解析");
        }
    }

    private List<String> parseOptions(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof String string) return objectMapper.readValue(string, new TypeReference<>() {});
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.CONFLICT, "动态属性枚举定义无法解析");
        }
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(HttpStatus.BAD_REQUEST, "JSON数据格式错误"); }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean b ? b : value instanceof Number n ? n.intValue() != 0 : Boolean.parseBoolean(String.valueOf(value));
    }

    private int defaultSort(Integer value) { return value == null ? 0 : value; }
    private BigDecimal defaultStock(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private Map<String, Object> findOne(Map<String, Object> row, String message) {
        if (row == null) throw notFound(message);
        return row;
    }

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
