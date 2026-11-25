package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 行级数据范围的解析与校验，对应 resources/mapper/DataScopeMapper.xml；只读、无行锁。
 * roleScopes 取当前用户所有角色的范围码，countXxxInScope 用于按 id 取详情前确认这一行在范围内；
 * 本层只返回事实，范围怎么合并由 DataScopeService 决定。
 */
@Mapper
public interface DataScopeMapper {

    // 当前用户的角色范围码（ALL/DEPT_AND_CHILD/DEPT/SELF 等），Service 取其中最宽的一个。
    List<String> roleScopes(@Param("userId") Long userId);

    // 详情前的范围校验：返回 0 即越权，Service 统一抛 404 而非 403，避免暴露 id 是否存在。
    int countRequisitionInScope(@Param("id") Long id, @Param("scope") String scope);

    int countPurchaseOrderInScope(@Param("id") Long id, @Param("scope") String scope);

    // 全部部门（含 parent_id 与启用状态），供 Service 展开部门子树。
    List<Map<String, Object>> departments();
}
