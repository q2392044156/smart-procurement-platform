package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 审计表的追加入口与全量读取，对应 resources/mapper/AuditMapper.xml；只有 insert 与 list。
 * insert 由 AuditAspect 在写请求结束时调用，入参顺序即审计表的字段顺序；list 按 id 倒序、不分页。
 * 刻意不做：不脱敏、不截断、不校验 userId 是否真实存在——审计表只追加，保留策略交给数据库。
 */
@Mapper
public interface AuditMapper {

    // 全量审计记录，按 id 倒序；没有过滤参数，量级控制靠数据库侧保留策略。
    List<Map<String, Object>> list();

    // 追加一条：result 取 SUCCESS/FAILED，errorMessage 只在失败时非空，durationMs 为切片耗时。
    int insert(@Param("userId") Long userId, @Param("username") String username,
               @Param("method") String method, @Param("path") String path,
               @Param("result") String result, @Param("errorMessage") String errorMessage,
               @Param("requestId") String requestId, @Param("durationMs") long durationMs);
}
