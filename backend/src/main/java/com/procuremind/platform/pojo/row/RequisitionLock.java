package com.procuremind.platform.pojo.row;

import java.math.BigDecimal;

/**
 * 采购申请加锁读（{@code RequisitionMapper.lockById}）的投影。
 * <p>列别名、驼峰转换与装配方式见 {@link PurchaseOrderLock}。
 *
 * @param applicantId  申请人，提交/取消/编辑时用于属主校验
 * @param departmentId 申请部门，提交时用于预算校验
 * @param budgetAmount 预算金额，提交时既做预算占用也作为流程变量
 * @param status       申请状态，决定能否编辑、提交、取消、删除
 */
public record RequisitionLock(
        Long applicantId,
        Long departmentId,
        BigDecimal budgetAmount,
        String status) {
}
