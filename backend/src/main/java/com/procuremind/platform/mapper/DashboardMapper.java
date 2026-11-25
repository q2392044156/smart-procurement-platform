package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    BigDecimal yearPurchaseAmount();

    long activeOrders();

    long lowStockCount();

    List<Map<String, Object>> orderStatus();

    List<Map<String, Object>> supplierRanking();

    List<Map<String, Object>> monthlyTrend();

    List<Map<String, Object>> lowStockMaterials();
}
