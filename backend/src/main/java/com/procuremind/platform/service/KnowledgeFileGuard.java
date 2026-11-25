package com.procuremind.platform.service;

import com.procuremind.platform.mapper.FileReferenceMapper;
import org.springframework.stereotype.Component;

/**
 * 告诉文件模块：知识库引用了哪些文件，避免删除对象后文档悬空。
 * 只依赖 mapper，不依赖知识库服务本身，避免 Bean 循环依赖。
 */
@Component
public class KnowledgeFileGuard implements FileReferenceChecker {
    private final FileReferenceMapper mapper;
    public KnowledgeFileGuard(FileReferenceMapper mapper) { this.mapper = mapper; }

    @Override
    public boolean isReferenced(Long fileId) {
        return mapper.countKnowledgeDocuments(fileId) + mapper.countKnowledgeVersions(fileId) > 0;
    }

    @Override
    public String referenceName() { return "知识库文档（含历史版本）"; }
}
