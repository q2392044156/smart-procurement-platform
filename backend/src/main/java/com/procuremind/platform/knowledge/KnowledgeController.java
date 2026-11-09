package com.procuremind.platform.knowledge;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口。原文上传走本模块自己的预签名策略，下载由服务层按可见范围授权。
 */
@RestController
@RequestMapping("/api/v1/kb")
@PreAuthorize("hasAuthority('kb:view')")
public class KnowledgeController {
    private final KnowledgeService service;
    public KnowledgeController(KnowledgeService service) { this.service = service; }

    @GetMapping("/documents")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int current,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.list(keyword, category, status, current, size));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/documents/{id}/download")
    public ApiResponse<Map<String, Object>> download(@PathVariable Long id) {
        return ApiResponse.ok(service.download(id));
    }

    @GetMapping("/documents/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable Long id) {
        Map<String, Object> file = service.publishedRaw(id);
        String contentType = String.valueOf(file.getOrDefault("contentType", "application/octet-stream"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body((byte[]) file.get("bytes"));
    }

    @PostMapping("/uploads/presign")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Map<String, Object>> presign(@Valid @RequestBody KnowledgeService.UploadRequest request) {
        return ApiResponse.ok(service.presignUpload(request));
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody KnowledgeService.CreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody KnowledgeService.UpdateRequest request) {
        service.update(id, request);
        return ApiResponse.ok("知识库文档已更新", null);
    }

    @PostMapping("/documents/{id}/versions")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Map<String, Object>> addVersion(@PathVariable Long id, @Valid @RequestBody KnowledgeService.VersionRequest request) {
        return ApiResponse.ok(Map.of("version", service.addVersion(id, request)));
    }

    @PatchMapping("/documents/{id}/status")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody KnowledgeService.StatusRequest request) {
        service.changeStatus(id, request.status());
        return ApiResponse.ok("文档状态已更新", null);
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, String>>> categories() {
        return ApiResponse.ok(List.of(
                Map.of("code", "POLICY", "name", "制度规范"),
                Map.of("code", "PROCEDURE", "name", "流程指引"),
                Map.of("code", "CONTRACT", "name", "合同协议"),
                Map.of("code", "SUPPLIER", "name", "供应商资料"),
                Map.of("code", "MANUAL", "name", "产品手册"),
                Map.of("code", "TEMPLATE", "name", "模板表单"),
                Map.of("code", "OTHER", "name", "其他资料")));
    }
}
