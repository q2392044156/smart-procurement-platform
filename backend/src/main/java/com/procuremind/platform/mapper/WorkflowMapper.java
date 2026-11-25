package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审批回调的业务状态写回，对应 resources/mapper/WorkflowMapper.xml；只有两个 update，没有查询、没有行锁。
 * 单独成接口是为了避免 WorkflowService 反向依赖两个业务 Mapper；本层不判断状态合法性——
 * 调用方必须先用业务 Service 检查当前状态，否则会覆盖并发流转。
 */
@Mapper
public interface WorkflowMapper {

    // 申请状态回写：带 reason，驳回原因随状态一起落库。
    int updateRequisitionStatus(@Param("id") Long id, @Param("status") String status,
                                @Param("reason") String reason, @Param("userId") Long userId);

    // 订单状态回写：不带 reason，订单表没有驳回原因字段。
    int updatePurchaseOrderStatus(@Param("id") Long id, @Param("status") String status,
                                  @Param("userId") Long userId);
}
