package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.procuremind.platform.service.FileReferenceChecker;

    // 文件引用计数，供 FileReferenceChecker 决定能否删除；只读、无行锁、无写方法。
    // 两张表分别统计：文档表直挂文件，版本表挂历史版本文件，任一非 0 都不能删底层对象。
/** 文件被引用情况的计数查询，供各业务模块的 FileReferenceChecker 使用。 */
@Mapper
public interface FileReferenceMapper {

    // 引用该文件的文档数（未删除）。
    long countKnowledgeDocuments(@Param("fileId") Long fileId);

    // 引用该文件的历史版本数（未删除）。
    long countKnowledgeVersions(@Param("fileId") Long fileId);
}
