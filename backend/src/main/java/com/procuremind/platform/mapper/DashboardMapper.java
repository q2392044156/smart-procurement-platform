package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 看板用的无参聚合，对应 resources/mapper/DashboardMapper.xml；全部只读。
 * 刻意不带 scope 与时间参数：看板是全局经营视角，年度口径由 SQL 内按当前自然年取，结果与调用者身份无关。
 */
@Mapper
public interface DashboardMapper {

    // 本年采购金额汇总，BigDecimal 返回，无数据时由 SQL 兜底成 0 而不是 null。
    BigDecimal yearPurchaseAmount();

    // 在途订单数与低库存物资数两个标量，供首屏卡片直接渲染。
    // 用 int 而不是 long：XML 里已是 cast(count(*) as signed)，Long 会被 Jackson 写成字符串。
    int activeOrders();

    int lowStockCount();

    // 订单状态分布与供应商金额排名两个聚合，前端分别画饼图与柱状图。
    List<Map<String, Object>> orderStatus();

    List<Map<String, Object>> supplierRanking();

    // 月度趋势（近 12 个月按月分组）与低库存明细清单，后者带物资与仓库信息。
    List<Map<String, Object>> monthlyTrend();

    List<Map<String, Object>> lowStockMaterials();
}
