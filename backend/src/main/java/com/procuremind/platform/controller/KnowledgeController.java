package com.procuremind.platform.controller;

import com.procuremind.platform.service.KnowledgeService;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.KnowledgeCreateRequest;
import com.procuremind.platform.pojo.request.KnowledgeUpdateRequest;
import com.procuremind.platform.pojo.request.KnowledgeVersionRequest;
import com.procuremind.platform.pojo.request.KnowledgeStatusRequest;
import com.procuremind.platform.pojo.request.KnowledgeUploadRequest;

/**
 * 知识库管理接口。原文上传走本模块自己的预签名策略，下载由服务层按可见范围授权。
 * <p>路由前缀 {@code /api/v1/kb}，类级 kb:view 可读；上传、建文档、改文档、加版本、改状态、删除都需 kb:manage。</p>
 * <p>可见性在 Service 里按本人/部门子树/角色过滤，本类只做参数绑定；原文下载与 raw 输出都不走对象存储直链。</p>
 */
@RestController
@RequestMapping("/api/v1/kb")
@PreAuthorize("hasAuthority('kb:view')")
public class KnowledgeController {
    private final KnowledgeService service;
    public KnowledgeController(KnowledgeService service) { this.service = service; }

    // 四个读端点：kb:view；列表按身份过滤可见范围，详情/下载/raw 都先校验可见性，越权一律 404。
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

    // 写端点六个：均需 kb:manage；presign 只发上传地址，建文档时校验分类、密级与初始状态。
    @PostMapping("/uploads/presign")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Map<String, Object>> presign(@Valid @RequestBody KnowledgeUploadRequest request) {
        return ApiResponse.ok(service.presignUpload(request));
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody KnowledgeCreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody KnowledgeUpdateRequest request) {
        service.update(id, request);
        return ApiResponse.ok("知识库文档已更新", null);
    }

    // 版本与状态：加版本生成新版本号，改状态控制发布/归档/下线，删除是软删除并让检索侧增量失效。
    @PostMapping("/documents/{id}/versions")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Map<String, Object>> addVersion(@PathVariable Long id, @Valid @RequestBody KnowledgeVersionRequest request) {
        return ApiResponse.ok(Map.of("version", service.addVersion(id, request)));
    }

    @PatchMapping("/documents/{id}/status")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody KnowledgeStatusRequest request) {
        service.changeStatus(id, request.status());
        return ApiResponse.ok("文档状态已更新", null);
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('kb:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    // 分类树：kb:view；一次性返回全部分类，不分页，供前端下拉与 Agent 过滤提示词使用。
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
