package com.procuremind.platform.controller;

import com.procuremind.platform.service.MasterDataService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.MaterialCategoryRequest;
import com.procuremind.platform.pojo.request.MaterialRequest;
import com.procuremind.platform.pojo.request.WarehouseRequest;
import com.procuremind.platform.pojo.request.UnitRequest;
import com.procuremind.platform.pojo.request.BrandRequest;
import com.procuremind.platform.pojo.request.MasterDataStatusRequest;

/**
 * 主数据维护：物资分类、物资、仓库、计量单位、品牌，路由前缀 /api/v1/master-data。
 * 类级 masterdata:view 覆盖全部读；写操作按子域抬到 masterdata:<子域>:edit，物资分类与物资还接受粗粒度的
 * masterdata:edit，仓库/单位/品牌则只认子域码。刻意不做：不做编码生成、不做引用检查，也不碰库存数量。
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('masterdata:view')")
public class MasterDataController {
    private final MasterDataService service;
    public MasterDataController(MasterDataService service) { this.service=service; }

    // 物资分类：读需 masterdata:view；写需 masterdata:edit 或 masterdata:category:edit，删除是软删除。
    @GetMapping("/material-categories")
    public ApiResponse<List<Map<String,Object>>> categories() {
        return ApiResponse.ok(service.categories());
    }
    @GetMapping("/material-categories/{id}") public ApiResponse<Map<String,Object>> category(@PathVariable Long id){return ApiResponse.ok(service.category(id));}
    @PostMapping("/material-categories") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:category:edit')")
    public ApiResponse<Map<String,Object>> createCategory(@Valid @RequestBody MaterialCategoryRequest r) {
        return ApiResponse.ok(Map.of("id",service.createCategory(r)));
    }
    @PutMapping("/material-categories/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:category:edit')") public ApiResponse<Void> updateCategory(@PathVariable Long id,@Valid @RequestBody MaterialCategoryRequest r){service.updateCategory(id,r);return ApiResponse.ok(null);}
    @DeleteMapping("/material-categories/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:category:edit')") public ApiResponse<Void> deleteCategory(@PathVariable Long id){service.deleteCategory(id);return ApiResponse.ok(null);}
    // 物资：读 masterdata:view（列表/详情/分页）；写需 masterdata:material:edit，启停用走 PATCH 状态端点。
    @GetMapping("/materials")
    public ApiResponse<List<Map<String,Object>>> materials(
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) String brand,
            @RequestParam(required=false) String category,
            @RequestParam(required=false) String specification,
            @RequestParam(required=false) String trackingMode) {
        return ApiResponse.ok(service.materials(keyword, brand, category, specification, trackingMode));
    }
    @GetMapping("/materials/{id}") public ApiResponse<Map<String,Object>> material(@PathVariable Long id){return ApiResponse.ok(service.material(id));}
    @GetMapping("/materials/page")
    public ApiResponse<Map<String,Object>> materialsPage(
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) String brand,
            @RequestParam(required=false) String category,
            @RequestParam(required=false) String specification,
            @RequestParam(required=false) String trackingMode,
            @RequestParam(defaultValue="1") int current,
            @RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(service.materialsPage(keyword, brand, category, specification, trackingMode, current, size));
    }
    @PostMapping("/materials") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')")
    public ApiResponse<Map<String,Object>> createMaterial(@Valid @RequestBody MaterialRequest r) {
        return ApiResponse.ok(Map.of("id",service.createMaterial(r)));
    }
    @PutMapping("/materials/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')") public ApiResponse<Void> updateMaterial(@PathVariable Long id,@Valid @RequestBody MaterialRequest r){service.updateMaterial(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/materials/{id}/status") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')") public ApiResponse<Void> materialStatus(@PathVariable Long id,@Valid @RequestBody MasterDataStatusRequest r){service.setMaterialStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/materials/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')") public ApiResponse<Void> deleteMaterial(@PathVariable Long id){service.deleteMaterial(id);return ApiResponse.ok(null);}

    // 仓库：读 masterdata:view；写只需 masterdata:warehouse:edit（不再叠加 masterdata:edit）。
    @GetMapping("/warehouses") public ApiResponse<List<Map<String,Object>>> warehouses(){return ApiResponse.ok(service.warehouses());}
    @GetMapping("/warehouses/{id}") public ApiResponse<Map<String,Object>> warehouse(@PathVariable Long id){return ApiResponse.ok(service.warehouse(id));}
    @PostMapping("/warehouses") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Map<String,Object>> createWarehouse(@Valid @RequestBody WarehouseRequest r){return ApiResponse.ok(Map.of("id",service.createWarehouse(r)));}
    @PutMapping("/warehouses/{id}") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Void> updateWarehouse(@PathVariable Long id,@Valid @RequestBody WarehouseRequest r){service.updateWarehouse(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/warehouses/{id}/status") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Void> warehouseStatus(@PathVariable Long id,@Valid @RequestBody MasterDataStatusRequest r){service.setWarehouseStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/warehouses/{id}") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Void> deleteWarehouse(@PathVariable Long id){service.deleteWarehouse(id);return ApiResponse.ok(null);}

    // 计量单位：读 masterdata:view；写需 masterdata:unit:edit，单位参与数量换算，改动会影响历史展示。
    @GetMapping("/units") public ApiResponse<List<Map<String,Object>>> units(){return ApiResponse.ok(service.units());}
    @GetMapping("/units/{id}") public ApiResponse<Map<String,Object>> unit(@PathVariable Long id){return ApiResponse.ok(service.unit(id));}
    @PostMapping("/units") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Map<String,Object>> createUnit(@Valid @RequestBody UnitRequest r){return ApiResponse.ok(Map.of("id",service.createUnit(r)));}
    @PutMapping("/units/{id}") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Void> updateUnit(@PathVariable Long id,@Valid @RequestBody UnitRequest r){service.updateUnit(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/units/{id}/status") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Void> unitStatus(@PathVariable Long id,@Valid @RequestBody MasterDataStatusRequest r){service.setUnitStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/units/{id}") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Void> deleteUnit(@PathVariable Long id){service.deleteUnit(id);return ApiResponse.ok(null);}

    // 品牌：读 masterdata:view；写需 masterdata:brand:edit，与分类同构；引用检查统一在 MasterDataService。
    @GetMapping("/brands") public ApiResponse<List<Map<String,Object>>> brands(){return ApiResponse.ok(service.brands());}
    @GetMapping("/brands/{id}") public ApiResponse<Map<String,Object>> brand(@PathVariable Long id){return ApiResponse.ok(service.brand(id));}
    @PostMapping("/brands") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Map<String,Object>> createBrand(@Valid @RequestBody BrandRequest r){return ApiResponse.ok(Map.of("id",service.createBrand(r)));}
    @PutMapping("/brands/{id}") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Void> updateBrand(@PathVariable Long id,@Valid @RequestBody BrandRequest r){service.updateBrand(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/brands/{id}/status") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Void> brandStatus(@PathVariable Long id,@Valid @RequestBody MasterDataStatusRequest r){service.setBrandStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/brands/{id}") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Void> deleteBrand(@PathVariable Long id){service.deleteBrand(id);return ApiResponse.ok(null);}

}
