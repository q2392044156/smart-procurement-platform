package com.procuremind.platform.controller;

import com.procuremind.platform.pojo.ApiResponse;
import com.procuremind.platform.pojo.FilePresignRequest;
import com.procuremind.platform.service.BusinessAttachmentService;
import com.procuremind.platform.service.FileStorageService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import com.procuremind.platform.pojo.request.AttachmentRequest;

@RestController @RequestMapping("/api/v1/files")
public class FileController {
    private final FileStorageService storage;
    private final BusinessAttachmentService attachments;
    public FileController(FileStorageService storage,BusinessAttachmentService attachments){this.storage=storage;this.attachments=attachments;}
    @PostMapping("/presign") public ApiResponse<Map<String,Object>> presign(@Valid @RequestBody FilePresignRequest r){return ApiResponse.ok(storage.presign(r));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> download(@PathVariable Long id){return ApiResponse.ok(storage.download(id));}
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){storage.delete(id);return ApiResponse.ok(null);}
    @GetMapping("/attachments/{businessType}/{businessId}") public ApiResponse<List<Map<String,Object>>> attachments(@PathVariable String businessType,@PathVariable Long businessId){return ApiResponse.ok(attachments.list(businessType,businessId));}
    @PostMapping("/attachments/{businessType}/{businessId}") @PreAuthorize("hasAnyAuthority('requisition:create','purchaseorder:edit','purchaseorder:update')") public ApiResponse<Map<String,Object>> attach(@PathVariable String businessType,@PathVariable Long businessId,@Valid @RequestBody AttachmentRequest r){return ApiResponse.ok(Map.of("id",attachments.attach(businessType,businessId,r)));}
    @DeleteMapping("/attachments/{attachmentId}") @PreAuthorize("hasAnyAuthority('requisition:create','purchaseorder:edit','purchaseorder:update')") public ApiResponse<Void> deleteAttachment(@PathVariable Long attachmentId){attachments.delete(attachmentId);return ApiResponse.ok(null);}
}
