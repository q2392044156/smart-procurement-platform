package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 部门年度预算与占用额的查询/写入，对应 resources/mapper/BudgetMapper.xml；无行锁、无 record 决策读。
 * upsert 按 (department_id, fiscal_year) 唯一键覆盖，userId 写到 updated_by；三个 sum 方法是三种口径，
 * 调用方相加才是“已占用”，单看任何一个都不完整。
 */
@Mapper
public interface BudgetMapper {

    // 年度预算列表：联部门表带出部门名与占用额，一次性返回，不分页。
    List<Map<String, Object>> list(@Param("fiscalYear") int fiscalYear);

    // 写入前的部门存在性校验，返回 0 表示部门不存在或已停用。
    int countDepartment(@Param("departmentId") Long departmentId);

    // upsert：部门+年度已存在则覆盖金额，否则插入；不保留历史版本。
    void upsert(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear,
                @Param("amount") BigDecimal amount, @Param("userId") Long userId);

    // 本年度已分配预算总额；下面两个 sum 分别是申请占用与直采订单占用，三者由 Service 相减得到可用额度。
    BigDecimal findAllocated(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear);

    BigDecimal sumRequisitionCommitted(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear);

    // 不挂在申请上的直采订单占用，与 sumRequisitionCommitted 相加才是总占用。
    BigDecimal sumDirectOrderCommitted(@Param("departmentId") Long departmentId, @Param("fiscalYear") int fiscalYear);
}
