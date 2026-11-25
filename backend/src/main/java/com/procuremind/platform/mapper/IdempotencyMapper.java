package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface IdempotencyMapper {

    int insertProcessing(@Param("key") String key, @Param("userId") Long userId,
                         @Param("operation") String operation);

    List<Map<String, Object>> find(@Param("ttlMinutes") int ttlMinutes, @Param("key") String key,
                                   @Param("userId") Long userId, @Param("operation") String operation);

    int markFailed(@Param("key") String key, @Param("userId") Long userId,
                   @Param("operation") String operation);

    int markCompleted(@Param("key") String key, @Param("userId") Long userId,
                      @Param("operation") String operation, @Param("responseJson") String responseJson);

    int remove(@Param("key") String key, @Param("userId") Long userId,
               @Param("operation") String operation);
}
