package com.procuremind.platform.pojo.row;

/**
 * 采购订单加锁读的「只看状态」投影，供收货时判断订单是否处于可收货状态。
 *
 * @param status 订单状态，只有 APPROVED / PARTIALLY_RECEIVED 允许收货
 */
public record PurchaseOrderStatus(String status) {
}
