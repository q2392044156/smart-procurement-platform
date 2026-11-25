package com.procuremind.platform.pojo.row;

/**
 * 采购申请加锁读的「只看状态」投影，供转订单时判断申请是否已审批。
 *
 * @param status 申请状态，只有 APPROVED 才允许转订单
 */
public record RequisitionStatus(String status) {
}
