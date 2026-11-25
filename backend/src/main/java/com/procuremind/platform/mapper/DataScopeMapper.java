package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataScopeMapper {

    List<String> roleScopes(@Param("userId") Long userId);

    int countRequisitionInScope(@Param("id") Long id, @Param("scope") String scope);

    int countPurchaseOrderInScope(@Param("id") Long id, @Param("scope") String scope);

    List<Map<String, Object>> departments();
}
