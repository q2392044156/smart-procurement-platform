package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 合同档案的读写映射，对应 resources/mapper/ContractMapper.xml；无行锁、无 record 决策读。
 * 状态流转只走 activate/terminate 两个定点更新，不接受任意状态赋值；softDelete 一律带 userId 记删除人。
 * expireDue 是给定时任务用的批量失效（结束日期已过的生效合同），也是唯一无参数、批量修改的方法。
 */
@Mapper
public interface ContractMapper {

    // countPage/page 共用同一套过滤：like 匹配合同号与名称、state 精确匹配状态，分页由 size/offset 控制。
    int countPage(@Param("like") String like, @Param("state") String state);

    List<Map<String, Object>> page(@Param("like") String like, @Param("state") String state,
                                   @Param("size") int size, @Param("offset") int offset);

    // 详情：带供应商名与关联订单号，供列表点开一次取全。
    Map<String, Object> findDetail(@Param("id") Long id);

    // insert/update：合同号唯一性先用 findIdByNo 查，金额与日期区间在 Service 校验。
    int insert(@Param("contractNo") String contractNo,
               @Param("name") String name,
               @Param("supplierId") Long supplierId,
               @Param("purchaseOrderId") Long purchaseOrderId,
               @Param("type") String type,
               @Param("amount") BigDecimal amount,
               @Param("signedDate") LocalDate signedDate,
               @Param("startDate") LocalDate startDate,
               @Param("endDate") LocalDate endDate,
               @Param("fileId") Long fileId,
               @Param("remark") String remark,
               @Param("userId") Long userId);

    Long findIdByNo(@Param("contractNo") String contractNo);

    int update(@Param("id") Long id,
               @Param("name") String name,
               @Param("supplierId") Long supplierId,
               @Param("purchaseOrderId") Long purchaseOrderId,
               @Param("type") String type,
               @Param("amount") BigDecimal amount,
               @Param("signedDate") LocalDate signedDate,
               @Param("startDate") LocalDate startDate,
               @Param("endDate") LocalDate endDate,
               @Param("fileId") Long fileId,
               @Param("remark") String remark,
               @Param("userId") Long userId);

    // activate/terminate/softDelete：定点改状态，是否允许迁移由 Service 结合 findStatus 判定。
    int activate(@Param("id") Long id, @Param("userId") Long userId);

    int terminate(@Param("id") Long id, @Param("userId") Long userId);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int expireDue();

    // findStatus 只取当前状态；findOrderSupplier 用于校验订单与供应商是否匹配。
    Map<String, Object> findStatus(@Param("id") Long id);

    int countSupplier(@Param("supplierId") Long supplierId);

    Long findOrderSupplier(@Param("purchaseOrderId") Long purchaseOrderId);
}
