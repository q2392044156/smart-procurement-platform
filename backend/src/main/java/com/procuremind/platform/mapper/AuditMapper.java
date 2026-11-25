package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditMapper {

    List<Map<String, Object>> list();

    int insert(@Param("userId") Long userId, @Param("username") String username,
               @Param("method") String method, @Param("path") String path,
               @Param("result") String result, @Param("errorMessage") String errorMessage,
               @Param("requestId") String requestId, @Param("durationMs") long durationMs);
}
