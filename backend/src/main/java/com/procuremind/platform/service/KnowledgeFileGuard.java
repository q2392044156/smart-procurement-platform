package com.procuremind.platform.service;

import com.procuremind.platform.mapper.FileReferenceMapper;
import org.springframework.stereotype.Component;

/**
 * 告诉文件模块：知识库引用了哪些文件，避免删除对象后文档悬空。
 * 只依赖 mapper，不依赖知识库服务本身，避免 Bean 循环依赖（KnowledgeService 依赖文件服务，反向依赖会成环）。
 *
 * <p>引用分两处统计：文档当前挂的附件，以及文档历史版本保留的附件；两处计数都为 0 才允许物理删除。
 * 只做只读计数、无事务、不缓存，删除前实时查询以保证判断准确。</p>
 */
@Component
public class KnowledgeFileGuard implements FileReferenceChecker {
    private final FileReferenceMapper mapper;
    public KnowledgeFileGuard(FileReferenceMapper mapper) { this.mapper = mapper; }

    /** 当前文档与历史版本任一命中即视为占用；两次查询都是按 fileId 的索引计数，不取明细。 */
    @Override
    public boolean isReferenced(Long fileId) {
        return mapper.countKnowledgeDocuments(fileId) + mapper.countKnowledgeVersions(fileId) > 0;
    }

    @Override
    public String referenceName() { return "知识库文档（含历史版本）"; }
}
