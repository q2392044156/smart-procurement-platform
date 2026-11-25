package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsMapper {

    Map<String, Object> supplierScorecard(@Param("year") int year,
                                          @Param("scope") String scope,
                                          @Param("supplierId") Long supplierId);

    List<Map<String, Object>> inventoryRisk();

    List<Map<String, Object>> spendByDepartment(@Param("year") int year, @Param("scope") String scope);

    List<Map<String, Object>> spendBySupplier(@Param("year") int year, @Param("scope") String scope);

    List<Map<String, Object>> spendByCategory(@Param("year") int year, @Param("scope") String scope);

    List<Map<String, Object>> spendByMonth(@Param("year") int year, @Param("scope") String scope);
}
