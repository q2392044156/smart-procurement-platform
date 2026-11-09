package com.procuremind.platform.agentintegration;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.knowledge.KnowledgeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 知识库对检索服务（harness / RAG）暴露的内部接口，供其按用户可见性增量同步。
 *
 * <p>只经内部令牌访问；返回的文档内容仍通过 {@link KnowledgeService#download} 做可见性校验。</p>
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeAgentController {
    private final KnowledgeService knowledge;

    public KnowledgeAgentController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    /** 当前身份可见的已发布文档清单（检索前用于过滤，避免越权召回）。 */
    @GetMapping("/visible-doc-ids")
    public ApiResponse<List<Map<String, Object>>> visibleDocuments() {
        return ApiResponse.ok(knowledge.visibleDocumentIds());
    }

    /** 指定时间之后发生变化的文档（含归档与删除），供增量重建索引。 */
    @GetMapping("/documents/changed")
    public ApiResponse<List<Map<String, Object>>> changed(@RequestParam(required = false) String since) {
        return ApiResponse.ok(knowledge.changedSince(parse(since)));
    }

    /** 校验可见性后返回预签名下载地址，供检索服务拉取原文。 */
    @GetMapping("/documents/{id}/content")
    public ApiResponse<Map<String, Object>> content(@PathVariable Long id) {
        return ApiResponse.ok(knowledge.publishedContent(id));
    }

    /** 直接返回已发布原文，避免预签名 URL 的 host 在 Agent/MCP 容器内不可达。 */
    @GetMapping("/documents/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable Long id) {
        Map<String, Object> file = knowledge.publishedRaw(id);
        String contentType = String.valueOf(file.getOrDefault("contentType", "application/octet-stream"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body((byte[]) file.get("bytes"));
    }

    private Instant parse(String since) {
        if (since == null || since.isBlank()) return Instant.EPOCH;
        try {
            return Instant.parse(since.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "since 必须是 ISO-8601 时间，例如 2026-01-01T00:00:00Z");
        }
    }
}
