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

/**
 * 文件与业务附件，路由前缀 /api/v1/files；类上无 @PreAuthorize，鉴权按用途分散到端点。
 * presign/download/delete 只要求已登录，归属与引用检查在 FileStorageService；附件读写才要求申请或订单的写权限码。
 * 刻意不做：不生成签名、不判断对象存储状态、不校验附件与业务单据的一致性——都在 storage 与 attachments 服务里。
 */
@RestController @RequestMapping("/api/v1/files")
public class FileController {
    private final FileStorageService storage;
    private final BusinessAttachmentService attachments;
    public FileController(FileStorageService storage,BusinessAttachmentService attachments){this.storage=storage;this.attachments=attachments;}
    // 预签名上传：已登录即可；返回对象键与上传地址，元数据在回调时落库。
    @PostMapping("/presign") public ApiResponse<Map<String,Object>> presign(@Valid @RequestBody FilePresignRequest r){return ApiResponse.ok(storage.presign(r));}
    // 下载 / 删除：已登录即可；按归属与引用计数在 Service 判定，删除是软删除而不是删对象。
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> download(@PathVariable Long id){return ApiResponse.ok(storage.download(id));}
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){storage.delete(id);return ApiResponse.ok(null);}
    // 附件列表：已登录即可；按 businessType+businessId 列出未删除附件。
    @GetMapping("/attachments/{businessType}/{businessId}") public ApiResponse<List<Map<String,Object>>> attachments(@PathVariable String businessType,@PathVariable Long businessId){return ApiResponse.ok(attachments.list(businessType,businessId));}
    // 上传附件：需 requisition:create 或 purchaseorder:edit/update；写入业务单据与文件的关联。
    @PostMapping("/attachments/{businessType}/{businessId}") @PreAuthorize("hasAnyAuthority('requisition:create','purchaseorder:edit','purchaseorder:update')") public ApiResponse<Map<String,Object>> attach(@PathVariable String businessType,@PathVariable Long businessId,@Valid @RequestBody AttachmentRequest r){return ApiResponse.ok(Map.of("id",attachments.attach(businessType,businessId,r)));}
    // 删除附件：同一组权限码；只解绑关系，不删对象存储里的文件。
    @DeleteMapping("/attachments/{attachmentId}") @PreAuthorize("hasAnyAuthority('requisition:create','purchaseorder:edit','purchaseorder:update')") public ApiResponse<Void> deleteAttachment(@PathVariable Long attachmentId){attachments.delete(attachmentId);return ApiResponse.ok(null);}
}
