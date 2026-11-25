package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface UserDirectoryMapper {

    Map<String, Object> findByUsername(@Param("username") String username);

    Map<String, Object> findCurrent(@Param("id") Long id);

    java.util.List<String> roleCodes(@Param("userId") Long userId);

    java.util.List<String> permissionCodes(@Param("userId") Long userId);
}
