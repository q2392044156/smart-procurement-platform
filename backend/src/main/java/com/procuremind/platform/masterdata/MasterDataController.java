package com.procuremind.platform.masterdata;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('masterdata:view')")
public class MasterDataController {
    private final MasterDataService service;
    public MasterDataController(MasterDataService service) { this.service=service; }

    @GetMapping("/material-categories")
    public ApiResponse<List<Map<String,Object>>> categories() {
        return ApiResponse.ok(service.categories());
    }
    @GetMapping("/material-categories/{id}") public ApiResponse<Map<String,Object>> category(@PathVariable Long id){return ApiResponse.ok(service.category(id));}
    @PostMapping("/material-categories") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:category:edit')")
    public ApiResponse<Map<String,Object>> createCategory(@Valid @RequestBody MasterDataService.CategoryRequest r) {
        return ApiResponse.ok(Map.of("id",service.createCategory(r)));
    }
    @PutMapping("/material-categories/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:category:edit')") public ApiResponse<Void> updateCategory(@PathVariable Long id,@Valid @RequestBody MasterDataService.CategoryRequest r){service.updateCategory(id,r);return ApiResponse.ok(null);}
    @DeleteMapping("/material-categories/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:category:edit')") public ApiResponse<Void> deleteCategory(@PathVariable Long id){service.deleteCategory(id);return ApiResponse.ok(null);}
    @GetMapping("/materials")
    public ApiResponse<List<Map<String,Object>>> materials(@RequestParam(required=false) String keyword) {
        return ApiResponse.ok(service.materials(keyword));
    }
    @GetMapping("/materials/{id}") public ApiResponse<Map<String,Object>> material(@PathVariable Long id){return ApiResponse.ok(service.material(id));}
    @GetMapping("/materials/page") public ApiResponse<Map<String,Object>> materialsPage(@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.materialsPage(keyword,current,size));}
    @PostMapping("/materials") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')")
    public ApiResponse<Map<String,Object>> createMaterial(@Valid @RequestBody MasterDataService.MaterialRequest r) {
        return ApiResponse.ok(Map.of("id",service.createMaterial(r)));
    }
    @PutMapping("/materials/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')") public ApiResponse<Void> updateMaterial(@PathVariable Long id,@Valid @RequestBody MasterDataService.MaterialRequest r){service.updateMaterial(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/materials/{id}/status") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')") public ApiResponse<Void> materialStatus(@PathVariable Long id,@Valid @RequestBody MasterDataService.StatusRequest r){service.setMaterialStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/materials/{id}") @PreAuthorize("hasAnyAuthority('masterdata:edit','masterdata:material:edit')") public ApiResponse<Void> deleteMaterial(@PathVariable Long id){service.deleteMaterial(id);return ApiResponse.ok(null);}

    @GetMapping("/warehouses") public ApiResponse<List<Map<String,Object>>> warehouses(){return ApiResponse.ok(service.warehouses());}
    @GetMapping("/warehouses/{id}") public ApiResponse<Map<String,Object>> warehouse(@PathVariable Long id){return ApiResponse.ok(service.warehouse(id));}
    @PostMapping("/warehouses") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Map<String,Object>> createWarehouse(@Valid @RequestBody MasterDataService.WarehouseRequest r){return ApiResponse.ok(Map.of("id",service.createWarehouse(r)));}
    @PutMapping("/warehouses/{id}") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Void> updateWarehouse(@PathVariable Long id,@Valid @RequestBody MasterDataService.WarehouseRequest r){service.updateWarehouse(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/warehouses/{id}/status") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Void> warehouseStatus(@PathVariable Long id,@Valid @RequestBody MasterDataService.StatusRequest r){service.setWarehouseStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/warehouses/{id}") @PreAuthorize("hasAuthority('masterdata:warehouse:edit')") public ApiResponse<Void> deleteWarehouse(@PathVariable Long id){service.deleteWarehouse(id);return ApiResponse.ok(null);}

    @GetMapping("/units") public ApiResponse<List<Map<String,Object>>> units(){return ApiResponse.ok(service.units());}
    @GetMapping("/units/{id}") public ApiResponse<Map<String,Object>> unit(@PathVariable Long id){return ApiResponse.ok(service.unit(id));}
    @PostMapping("/units") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Map<String,Object>> createUnit(@Valid @RequestBody MasterDataService.UnitRequest r){return ApiResponse.ok(Map.of("id",service.createUnit(r)));}
    @PutMapping("/units/{id}") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Void> updateUnit(@PathVariable Long id,@Valid @RequestBody MasterDataService.UnitRequest r){service.updateUnit(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/units/{id}/status") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Void> unitStatus(@PathVariable Long id,@Valid @RequestBody MasterDataService.StatusRequest r){service.setUnitStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/units/{id}") @PreAuthorize("hasAuthority('masterdata:unit:edit')") public ApiResponse<Void> deleteUnit(@PathVariable Long id){service.deleteUnit(id);return ApiResponse.ok(null);}

    @GetMapping("/brands") public ApiResponse<List<Map<String,Object>>> brands(){return ApiResponse.ok(service.brands());}
    @GetMapping("/brands/{id}") public ApiResponse<Map<String,Object>> brand(@PathVariable Long id){return ApiResponse.ok(service.brand(id));}
    @PostMapping("/brands") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Map<String,Object>> createBrand(@Valid @RequestBody MasterDataService.BrandRequest r){return ApiResponse.ok(Map.of("id",service.createBrand(r)));}
    @PutMapping("/brands/{id}") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Void> updateBrand(@PathVariable Long id,@Valid @RequestBody MasterDataService.BrandRequest r){service.updateBrand(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/brands/{id}/status") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Void> brandStatus(@PathVariable Long id,@Valid @RequestBody MasterDataService.StatusRequest r){service.setBrandStatus(id,r.enabled());return ApiResponse.ok(null);}
    @DeleteMapping("/brands/{id}") @PreAuthorize("hasAuthority('masterdata:brand:edit')") public ApiResponse<Void> deleteBrand(@PathVariable Long id){service.deleteBrand(id);return ApiResponse.ok(null);}

}
