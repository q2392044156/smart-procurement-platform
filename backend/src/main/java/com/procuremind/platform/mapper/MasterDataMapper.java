package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 主数据（分类/动态属性/物资/仓库/单位/品牌）的读写映射。SQL 全部在 resources/mapper/MasterDataMapper.xml。 */
@Mapper
public interface MasterDataMapper {

    // ---------- 物资分类 ----------

    List<Map<String, Object>> listCategories();

    Map<String, Object> findCategory(@Param("id") Long id);

    void insertCategory(@Param("parentId") Long parentId, @Param("code") String code,
                        @Param("name") String name, @Param("description") String description,
                        @Param("sortOrder") int sortOrder, @Param("userId") Long userId);

    Long findCategoryIdByCode(@Param("code") String code);

    void updateCategory(@Param("id") Long id, @Param("parentId") Long parentId, @Param("code") String code,
                        @Param("name") String name, @Param("description") String description,
                        @Param("sortOrder") int sortOrder, @Param("userId") Long userId);

    int countCategoryChildren(@Param("id") Long id);

    int countCategoryById(@Param("id") Long id);

    /** 上级分类的 parent_id，可为空；供循环检测逐级上溯。 */
    Long findParentId(@Param("id") Long id);

    void deleteCategoryAttributes(@Param("categoryId") Long categoryId);

    void softDeleteCategory(@Param("id") Long id, @Param("userId") Long userId);

    // ---------- 分类动态属性 ----------

    List<Map<String, Object>> listCategoryAttributes(@Param("categoryId") Long categoryId);

    void insertCategoryAttribute(@Param("categoryId") Long categoryId, @Param("code") String code,
                                 @Param("name") String name, @Param("valueType") String valueType,
                                 @Param("required") Boolean required, @Param("optionsJson") String optionsJson,
                                 @Param("sortOrder") int sortOrder);

    List<Map<String, Object>> listMaterialAttributeValues(@Param("categoryId") Long categoryId);

    // ---------- 物资 ----------

    List<Map<String, Object>> listMaterials(@Param("keyword") String keyword, @Param("brand") String brand,
                                            @Param("category") String category, @Param("specification") String specification,
                                            @Param("trackingMode") String trackingMode);

    long countMaterialsPage(@Param("keyword") String keyword, @Param("brand") String brand,
                            @Param("category") String category, @Param("specification") String specification,
                            @Param("trackingMode") String trackingMode);

    List<Map<String, Object>> pageMaterials(@Param("keyword") String keyword, @Param("brand") String brand,
                                            @Param("category") String category, @Param("specification") String specification,
                                            @Param("trackingMode") String trackingMode,
                                            @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findMaterial(@Param("id") Long id);

    void insertMaterial(@Param("code") String code, @Param("name") String name,
                        @Param("categoryId") Long categoryId, @Param("unitId") Long unitId,
                        @Param("brandId") Long brandId, @Param("specification") String specification,
                        @Param("trackingMode") String trackingMode, @Param("minStock") BigDecimal minStock,
                        @Param("attributesJson") String attributesJson, @Param("userId") Long userId);

    Long findMaterialIdByCode(@Param("code") String code);

    void updateMaterial(@Param("id") Long id, @Param("name") String name, @Param("categoryId") Long categoryId,
                        @Param("unitId") Long unitId, @Param("brandId") Long brandId,
                        @Param("specification") String specification, @Param("trackingMode") String trackingMode,
                        @Param("minStock") BigDecimal minStock, @Param("attributesJson") String attributesJson,
                        @Param("userId") Long userId);

    void updateMaterialStatus(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("userId") Long userId);

    /* 物资删除前的引用计数，逐表与原来一致。 */
    int countRequisitionItemByMaterial(@Param("materialId") Long materialId);

    int countPurchaseOrderItemByMaterial(@Param("materialId") Long materialId);

    int countReceiptItemByMaterial(@Param("materialId") Long materialId);

    int countBalanceByMaterial(@Param("materialId") Long materialId);

    int countLedgerByMaterial(@Param("materialId") Long materialId);

    int countMaterialById(@Param("id") Long id);

    void softDeleteMaterial(@Param("id") Long id, @Param("userId") Long userId);

    int countMaterialByCategory(@Param("categoryId") Long categoryId);

    // ---------- 仓库 ----------

    List<Map<String, Object>> listWarehouses();

    Map<String, Object> findWarehouse(@Param("id") Long id);

    void insertWarehouse(@Param("code") String code, @Param("name") String name,
                         @Param("address") String address, @Param("manager") String manager,
                         @Param("userId") Long userId);

    Long findWarehouseIdByCode(@Param("code") String code);

    void updateWarehouse(@Param("id") Long id, @Param("code") String code, @Param("name") String name,
                         @Param("address") String address, @Param("manager") String manager,
                         @Param("userId") Long userId);

    void updateWarehouseStatus(@Param("id") Long id, @Param("enabled") boolean enabled,
                               @Param("userId") Long userId);

    int countWarehouseById(@Param("id") Long id, @Param("enabled") boolean enabled);

    int countBalanceWithStock(@Param("warehouseId") Long warehouseId);

    /* 仓库删除前的引用计数，逐表与原来一致。 */
    int countReceiptByWarehouse(@Param("warehouseId") Long warehouseId);

    int countBalanceByWarehouse(@Param("warehouseId") Long warehouseId);

    int countLedgerByWarehouse(@Param("warehouseId") Long warehouseId);

    int countBatchByWarehouse(@Param("warehouseId") Long warehouseId);

    int countSerialByWarehouse(@Param("warehouseId") Long warehouseId);

    void softDeleteWarehouse(@Param("id") Long id, @Param("userId") Long userId);

    // ---------- 计量单位 ----------

    List<Map<String, Object>> listUnits();

    Map<String, Object> findUnit(@Param("id") Long id);

    void insertUnit(@Param("code") String code, @Param("name") String name,
                    @Param("precisionScale") Integer precisionScale, @Param("userId") Long userId);

    Long findUnitIdByCode(@Param("code") String code);

    void updateUnit(@Param("id") Long id, @Param("code") String code, @Param("name") String name,
                    @Param("precisionScale") Integer precisionScale, @Param("userId") Long userId);

    void updateUnitStatus(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("userId") Long userId);

    int countUnitById(@Param("id") Long id, @Param("enabled") boolean enabled);

    int countUnitUsedInPurchaseOrders(@Param("id") Long id);

    Integer findUnitPrecisionScale(@Param("id") Long id);

    int countMaterialByUnit(@Param("unitId") Long unitId);

    void softDeleteUnit(@Param("id") Long id, @Param("userId") Long userId);

    // ---------- 品牌 ----------

    List<Map<String, Object>> listBrands();

    Map<String, Object> findBrand(@Param("id") Long id);

    void insertBrand(@Param("code") String code, @Param("name") String name, @Param("userId") Long userId);

    Long findBrandIdByCode(@Param("code") String code);

    void updateBrand(@Param("id") Long id, @Param("code") String code, @Param("name") String name,
                     @Param("userId") Long userId);

    void updateBrandStatus(@Param("id") Long id, @Param("enabled") boolean enabled, @Param("userId") Long userId);

    int countBrandById(@Param("id") Long id, @Param("enabled") boolean enabled);

    int countMaterialByBrand(@Param("brandId") Long brandId);

    void softDeleteBrand(@Param("id") Long id, @Param("userId") Long userId);
}
