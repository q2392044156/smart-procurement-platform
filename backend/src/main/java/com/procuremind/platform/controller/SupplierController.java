package com.procuremind.platform.controller;

import com.procuremind.platform.service.SupplierService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.SupplierRequest;
import com.procuremind.platform.pojo.request.SupplierStatusRequest;
import com.procuremind.platform.pojo.request.SupplierContactRequest;
import com.procuremind.platform.pojo.request.SupplierQualificationRequest;
import com.procuremind.platform.pojo.request.SupplierEvaluationRequest;

/**
 * 供应商主体与三类子档案（联系人、资质、绩效评价），路由前缀 /api/v1/suppliers。
 * 类级 supplier:view 覆盖全部读；写用 hasAnyAuthority 的粗粒度码兜底（supplier:edit 或 supplier:profile:edit），
 * 资质与绩效另给更细的 supplier:qualification:edit / supplier:evaluation:edit。刻意不做：不校验信用码唯一性、
 * 不维护主联系人约束、不直接给评分赋值——唯一性在 Service，评分由 SupplierMapper.recalculateScore 汇总。
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("hasAuthority('supplier:view')")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service){this.service=service;}
    // 供应商主体：读 supplier:view（列表/详情/分页）；增改、改状态、删除需 supplier:edit 或 supplier:profile:edit。
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) String keyword){return ApiResponse.ok(service.suppliers(keyword));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable Long id){return ApiResponse.ok(service.supplier(id));}
    @GetMapping("/page") public ApiResponse<Map<String,Object>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.page(keyword,status,current,size));}
    @PostMapping @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody SupplierRequest r){return ApiResponse.ok(Map.of("id",service.createSupplier(r)));}
    @PutMapping("/{id}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody SupplierRequest r){service.updateSupplier(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/{id}/status") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> status(@PathVariable Long id,@Valid @RequestBody SupplierStatusRequest r){service.setSupplierStatus(id,r.status());return ApiResponse.ok(null);}
    // 删除一律软删除并带 userId，历史单据仍可回读；本类不做行级范围过滤，供应商档案是全公司共享的。
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> delete(@PathVariable Long id){service.deleteSupplier(id);return ApiResponse.ok(null);}

    // 联系人：读 supplier:view；增改删需 supplier:profile:edit，主联系人的提升/清空同样在这个权限下。
    @GetMapping("/{supplierId}/contacts") public ApiResponse<List<Map<String,Object>>> contacts(@PathVariable Long supplierId){return ApiResponse.ok(service.contacts(supplierId));}
    @PostMapping("/{supplierId}/contacts") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Map<String,Object>> createContact(@PathVariable Long supplierId,@Valid @RequestBody SupplierContactRequest r){return ApiResponse.ok(Map.of("id",service.createContact(supplierId,r)));}
    @PutMapping("/{supplierId}/contacts/{contactId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> updateContact(@PathVariable Long supplierId,@PathVariable Long contactId,@Valid @RequestBody SupplierContactRequest r){service.updateContact(supplierId,contactId,r);return ApiResponse.ok(null);}
    @DeleteMapping("/{supplierId}/contacts/{contactId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> deleteContact(@PathVariable Long supplierId,@PathVariable Long contactId){service.deleteContact(supplierId,contactId);return ApiResponse.ok(null);}

    // 资质：读 supplier:view；增改删需 supplier:qualification:edit，到期日参与提醒与合格判定。
    @GetMapping("/{supplierId}/qualifications") public ApiResponse<List<Map<String,Object>>> qualifications(@PathVariable Long supplierId){return ApiResponse.ok(service.qualifications(supplierId));}
    @PostMapping("/{supplierId}/qualifications") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:qualification:edit')") public ApiResponse<Map<String,Object>> createQualification(@PathVariable Long supplierId,@Valid @RequestBody SupplierQualificationRequest r){return ApiResponse.ok(Map.of("id",service.createQualification(supplierId,r)));}
    @PutMapping("/{supplierId}/qualifications/{qualificationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:qualification:edit')") public ApiResponse<Void> updateQualification(@PathVariable Long supplierId,@PathVariable Long qualificationId,@Valid @RequestBody SupplierQualificationRequest r){service.updateQualification(supplierId,qualificationId,r);return ApiResponse.ok(null);}
    @DeleteMapping("/{supplierId}/qualifications/{qualificationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:qualification:edit')") public ApiResponse<Void> deleteQualification(@PathVariable Long supplierId,@PathVariable Long qualificationId){service.deleteQualification(supplierId,qualificationId);return ApiResponse.ok(null);}

    // 绩效评价：读 supplier:view；增改删需 supplier:evaluation:edit，评分由 SupplierMapper.recalculateScore 汇总。
    @GetMapping("/{supplierId}/evaluations") public ApiResponse<List<Map<String,Object>>> evaluations(@PathVariable Long supplierId){return ApiResponse.ok(service.evaluations(supplierId));}
    @PostMapping("/{supplierId}/evaluations") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:evaluation:edit')") public ApiResponse<Map<String,Object>> createEvaluation(@PathVariable Long supplierId,@Valid @RequestBody SupplierEvaluationRequest r){return ApiResponse.ok(Map.of("id",service.createEvaluation(supplierId,r)));}
    @PutMapping("/{supplierId}/evaluations/{evaluationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:evaluation:edit')") public ApiResponse<Void> updateEvaluation(@PathVariable Long supplierId,@PathVariable Long evaluationId,@Valid @RequestBody SupplierEvaluationRequest r){service.updateEvaluation(supplierId,evaluationId,r);return ApiResponse.ok(null);}
    @DeleteMapping("/{supplierId}/evaluations/{evaluationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:evaluation:edit')") public ApiResponse<Void> deleteEvaluation(@PathVariable Long supplierId,@PathVariable Long evaluationId){service.deleteEvaluation(supplierId,evaluationId);return ApiResponse.ok(null);}
}
