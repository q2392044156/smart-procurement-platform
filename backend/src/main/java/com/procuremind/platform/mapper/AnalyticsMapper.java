package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分析类只读聚合，对应 resources/mapper/AnalyticsMapper.xml；没有写方法、没有行锁。
 * 除 inventoryRisk 外每个方法都带 scope 片断（DataScopeService 拼好的行级过滤），scope 为空即不过滤，
 * 因此“传 null 就全量”是刻意约定——调用方必须先按当前身份算好 scope，本层不再校验。
 */
@Mapper
public interface AnalyticsMapper {

    // 供应商记分卡：按年聚合订单数、金额、准时率与质量分；supplierId 决定只看一家还是看排名。
    Map<String, Object> supplierScorecard(@Param("year") int year,
                                          @Param("scope") String scope,
                                          @Param("supplierId") Long supplierId);

    // 库存风险：低库存与呆滞清单，无参数，因此也不做行级范围过滤。
    List<Map<String, Object>> inventoryRisk();

    // 四个 spendBy*：按部门/供应商/品类/月份拆分花费，共用同一段 scope 过滤，前端按 groupBy 只调其中一个。
    List<Map<String, Object>> spendByDepartment(@Param("year") int year, @Param("scope") String scope);

    List<Map<String, Object>> spendBySupplier(@Param("year") int year, @Param("scope") String scope);

    List<Map<String, Object>> spendByCategory(@Param("year") int year, @Param("scope") String scope);

    List<Map<String, Object>> spendByMonth(@Param("year") int year, @Param("scope") String scope);
}
