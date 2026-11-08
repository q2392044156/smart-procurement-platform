package com.procuremind.platform.knowledge;

import com.procuremind.platform.file.FileReferenceChecker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 告诉文件模块：知识库引用了哪些文件，避免删除对象后文档悬空。
 * 只依赖 JdbcTemplate，不依赖知识库服务本身，避免 Bean 循环依赖。
 */
@Component
public class KnowledgeFileGuard implements FileReferenceChecker {
    private final JdbcTemplate jdbc;
    public KnowledgeFileGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean isReferenced(Long fileId) {
        long current = count("select count(*) from kb_document where file_id=? and deleted=0", fileId);
        long history = count("select count(*) from kb_document_version v join kb_document d on d.id=v.document_id where v.file_id=? and d.deleted=0", fileId);
        return current + history > 0;
    }

    @Override
    public String referenceName() { return "知识库文档（含历史版本）"; }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
