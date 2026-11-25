package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 认证与授权过滤器用的只读目录查询，对应 resources/mapper/UserDirectoryMapper.xml；无写方法、无行锁。
 * 每个请求都会走一次（JWT 过滤器或内部令牌过滤器），因此都按 username/id 走唯一索引；
 * 本层不做角色继承展开，继承与权限集合合并都在 Service 侧。
 */
@Mapper
public interface UserDirectoryMapper {

    // findByUsername 含 password_hash，只给登录校验用，不能直接回给响应；findCurrent 是 /auth/me 的来源。
    Map<String, Object> findByUsername(@Param("username") String username);

    Map<String, Object> findCurrent(@Param("id") Long id);

    // 角色码与权限码分开取，过滤器把它们拼成 ROLE_* 与裸权限码两种 authority。
    java.util.List<String> roleCodes(@Param("userId") Long userId);

    java.util.List<String> permissionCodes(@Param("userId") Long userId);
}
