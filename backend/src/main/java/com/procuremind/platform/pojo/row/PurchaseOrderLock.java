package com.procuremind.platform.pojo.row;

import java.math.BigDecimal;

/**
 * 采购订单加锁读（select ... for update）的投影。
 * <p>字段名必须与 {@code PurchaseOrderMapper.xml} 中 {@code lockById} 的列别名一一对应：
 * 列名经 map-underscore-to-camel-case 转换成 record 构造器参数名，再由
 * arg-name-based-constructor-auto-mapping 装配。漏一列不会报错，只会让对应组件为 null，
 * 所以 {@code LockMappingTest} 会逐个断言这些字段非空。
 *
 * @param orderNo       订单号
 * @param purchaserId   创建人（提交、取消、编辑时用于属主校验）
 * @param requisitionId 来源采购申请，取消/删除时用于回滚申请状态
 * @param supplierId    供应商
 * @param status        订单状态，决定能否提交、取消、关闭、删除
 * @param totalAmount   订单总额，发起审批流程时作为流程变量
 */
public record PurchaseOrderLock(
        String orderNo,
        Long purchaserId,
        Long requisitionId,
        Long supplierId,
        String status,
        BigDecimal totalAmount) {
}
