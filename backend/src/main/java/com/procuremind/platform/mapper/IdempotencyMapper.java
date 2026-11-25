package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 幂等键状态机，对应 resources/mapper/IdempotencyMapper.xml；无行锁，靠唯一键冲突表达“正在处理”。
 * 生命周期：insertProcessing → (markCompleted | markFailed) → 过期后 remove；
 * find 带 ttlMinutes，只认完成且未过期的记录，因此过期重放会重新执行而不是回放旧结果。
 */
@Mapper
public interface IdempotencyMapper {

    // 抢占幂等键：唯一键冲突即表示同 key 正在处理，调用方据此直接拒绝重复请求。
    int insertProcessing(@Param("key") String key, @Param("userId") Long userId,
                         @Param("operation") String operation);

    // 取已完成且未过期的结果；返回 List，调用方取第一条，空表示没有可复用的结果。
    List<Map<String, Object>> find(@Param("ttlMinutes") int ttlMinutes, @Param("key") String key,
                                   @Param("userId") Long userId, @Param("operation") String operation);

    // 失败标记：置为可重试，允许同 key 再次进入。
    int markFailed(@Param("key") String key, @Param("userId") Long userId,
                   @Param("operation") String operation);

    // 完成落库：写 responseJson 供重放直接回放响应体；remove 用于需要手动失效的场景。
    int markCompleted(@Param("key") String key, @Param("userId") Long userId,
                      @Param("operation") String operation, @Param("responseJson") String responseJson);

    int remove(@Param("key") String key, @Param("userId") Long userId,
               @Param("operation") String operation);
}
