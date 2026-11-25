package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 文件被引用情况的计数查询，供各业务模块的 FileReferenceChecker 使用。 */
@Mapper
public interface FileReferenceMapper {

    long countKnowledgeDocuments(@Param("fileId") Long fileId);

    long countKnowledgeVersions(@Param("fileId") Long fileId);
}
