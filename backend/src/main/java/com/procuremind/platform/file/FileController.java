package com.procuremind.platform.file;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController @RequestMapping("/api/v1/files")
public class FileController {
    private final FileStorageService storage;
    private final BusinessAttachmentService attachments;
    public FileController(FileStorageService storage,BusinessAttachmentService attachments){this.storage=storage;this.attachments=attachments;}
    @PostMapping("/presign") public ApiResponse<Map<String,Object>> presign(@Valid @RequestBody PresignRequest r){return ApiResponse.ok(storage.presign(r));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> download(@PathVariable Long id){return ApiResponse.ok(storage.download(id));}
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){storage.delete(id);return ApiResponse.ok(null);}
    @GetMapping("/attachments/{businessType}/{businessId}") public ApiResponse<List<Map<String,Object>>> attachments(@PathVariable String businessType,@PathVariable Long businessId){return ApiResponse.ok(attachments.list(businessType,businessId));}
    @PostMapping("/attachments/{businessType}/{businessId}") @PreAuthorize("hasAnyAuthority('requisition:create','purchaseorder:edit','purchaseorder:update')") public ApiResponse<Map<String,Object>> attach(@PathVariable String businessType,@PathVariable Long businessId,@Valid @RequestBody BusinessAttachmentService.AttachmentRequest r){return ApiResponse.ok(Map.of("id",attachments.attach(businessType,businessId,r)));}
    @DeleteMapping("/attachments/{attachmentId}") @PreAuthorize("hasAnyAuthority('requisition:create','purchaseorder:edit','purchaseorder:update')") public ApiResponse<Void> deleteAttachment(@PathVariable Long attachmentId){attachments.delete(attachmentId);return ApiResponse.ok(null);}
    public record PresignRequest(@NotBlank @Size(max=255) String fileName,@NotBlank @Pattern(regexp="application/pdf|image/jpeg|image/png|application/octet-stream") String contentType,@NotNull @Min(1) @Max(52428800) Long size){}
}
