package com.procuremind.platform.supplier;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("hasAuthority('supplier:view')")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service){this.service=service;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) String keyword){return ApiResponse.ok(service.suppliers(keyword));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable Long id){return ApiResponse.ok(service.supplier(id));}
    @GetMapping("/page") public ApiResponse<Map<String,Object>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(service.page(keyword,status,current,size));}
    @PostMapping @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody SupplierService.SupplierRequest r){return ApiResponse.ok(Map.of("id",service.createSupplier(r)));}
    @PutMapping("/{id}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody SupplierService.SupplierRequest r){service.updateSupplier(id,r);return ApiResponse.ok(null);}
    @PatchMapping("/{id}/status") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> status(@PathVariable Long id,@Valid @RequestBody SupplierService.SupplierStatusRequest r){service.setSupplierStatus(id,r.status());return ApiResponse.ok(null);}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> delete(@PathVariable Long id){service.deleteSupplier(id);return ApiResponse.ok(null);}

    @GetMapping("/{supplierId}/contacts") public ApiResponse<List<Map<String,Object>>> contacts(@PathVariable Long supplierId){return ApiResponse.ok(service.contacts(supplierId));}
    @PostMapping("/{supplierId}/contacts") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Map<String,Object>> createContact(@PathVariable Long supplierId,@Valid @RequestBody SupplierService.ContactRequest r){return ApiResponse.ok(Map.of("id",service.createContact(supplierId,r)));}
    @PutMapping("/{supplierId}/contacts/{contactId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> updateContact(@PathVariable Long supplierId,@PathVariable Long contactId,@Valid @RequestBody SupplierService.ContactRequest r){service.updateContact(supplierId,contactId,r);return ApiResponse.ok(null);}
    @DeleteMapping("/{supplierId}/contacts/{contactId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:profile:edit')") public ApiResponse<Void> deleteContact(@PathVariable Long supplierId,@PathVariable Long contactId){service.deleteContact(supplierId,contactId);return ApiResponse.ok(null);}

    @GetMapping("/{supplierId}/qualifications") public ApiResponse<List<Map<String,Object>>> qualifications(@PathVariable Long supplierId){return ApiResponse.ok(service.qualifications(supplierId));}
    @PostMapping("/{supplierId}/qualifications") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:qualification:edit')") public ApiResponse<Map<String,Object>> createQualification(@PathVariable Long supplierId,@Valid @RequestBody SupplierService.QualificationRequest r){return ApiResponse.ok(Map.of("id",service.createQualification(supplierId,r)));}
    @PutMapping("/{supplierId}/qualifications/{qualificationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:qualification:edit')") public ApiResponse<Void> updateQualification(@PathVariable Long supplierId,@PathVariable Long qualificationId,@Valid @RequestBody SupplierService.QualificationRequest r){service.updateQualification(supplierId,qualificationId,r);return ApiResponse.ok(null);}
    @DeleteMapping("/{supplierId}/qualifications/{qualificationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:qualification:edit')") public ApiResponse<Void> deleteQualification(@PathVariable Long supplierId,@PathVariable Long qualificationId){service.deleteQualification(supplierId,qualificationId);return ApiResponse.ok(null);}

    @GetMapping("/{supplierId}/evaluations") public ApiResponse<List<Map<String,Object>>> evaluations(@PathVariable Long supplierId){return ApiResponse.ok(service.evaluations(supplierId));}
    @PostMapping("/{supplierId}/evaluations") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:evaluation:edit')") public ApiResponse<Map<String,Object>> createEvaluation(@PathVariable Long supplierId,@Valid @RequestBody SupplierService.EvaluationRequest r){return ApiResponse.ok(Map.of("id",service.createEvaluation(supplierId,r)));}
    @PutMapping("/{supplierId}/evaluations/{evaluationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:evaluation:edit')") public ApiResponse<Void> updateEvaluation(@PathVariable Long supplierId,@PathVariable Long evaluationId,@Valid @RequestBody SupplierService.EvaluationRequest r){service.updateEvaluation(supplierId,evaluationId,r);return ApiResponse.ok(null);}
    @DeleteMapping("/{supplierId}/evaluations/{evaluationId}") @PreAuthorize("hasAnyAuthority('supplier:edit','supplier:evaluation:edit')") public ApiResponse<Void> deleteEvaluation(@PathVariable Long supplierId,@PathVariable Long evaluationId){service.deleteEvaluation(supplierId,evaluationId);return ApiResponse.ok(null);}
}
