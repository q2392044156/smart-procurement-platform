package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlertMapper {

    List<Map<String, Object>> expiringQualifications(@Param("windowDays") int windowDays);

    List<Map<String, Object>> dueDeliveries(@Param("windowDays") int windowDays);

    List<Map<String, Object>> staleApprovals(@Param("windowHours") int windowHours);
}
