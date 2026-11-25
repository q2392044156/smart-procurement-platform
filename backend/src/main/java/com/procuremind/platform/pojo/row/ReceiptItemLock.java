package com.procuremind.platform.pojo.row;

import java.math.BigDecimal;

/**
 * 采购订单明细加锁读（{@code ReceiptMapper.lockOrderItem}）的投影。
 * <p>收货时用它判断剩余可收量与追踪方式；漏列会让超收校验静默失效，因此由
 * {@code LockMappingTest} 断言各字段非空。
 *
 * @param materialId       物资，入库时作为库存维度
 * @param quantity         订购数量
 * @param receivedQuantity 已收数量，与订购数量之差即剩余可收
 * @param trackingMode     QUANTITY / BATCH / SERIAL，决定批次号与序列号校验
 */
public record ReceiptItemLock(
        Long materialId,
        BigDecimal quantity,
        BigDecimal receivedQuantity,
        String trackingMode) {
}
