package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 到期与超时提醒的三条只读聚合，对应 resources/mapper/AlertMapper.xml；无写方法、无行锁。
 * 三份清单各由一个窗口参数驱动，不落库、不缓存，重复调用结果一致。
 * 刻意不做：三个方法都只接窗口参数，没有用户或部门参数，因此提醒对可见该权限的人全员一致。
 */
@Mapper
public interface AlertMapper {

    // 资质在 windowDays 天内到期（含今天）的供应商清单。
    List<Map<String, Object>> expiringQualifications(@Param("windowDays") int windowDays);

    // 未完结订单里交期落在 windowDays 天内的清单。
    List<Map<String, Object>> dueDeliveries(@Param("windowDays") int windowDays);

    // 停留超过 windowHours 小时仍未推进的审批实例，用来发现卡单。
    List<Map<String, Object>> staleApprovals(@Param("windowHours") int windowHours);
}
