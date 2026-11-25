package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BudgetMapper {

    List<Map<String, Object>> list(@Param("fiscalYear") int fiscalYear);

    int countDepartment(@Param("departmentId") Long departmentId);

    void upsert(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear,
                @Param("amount") BigDecimal amount, @Param("userId") Long userId);

    BigDecimal findAllocated(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear);

    BigDecimal sumRequisitionCommitted(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear);

    BigDecimal sumDirectOrderCommitted(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear);
}
