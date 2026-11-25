package com.procuremind.platform.pojo.row;

import java.math.BigDecimal;

/**
 * 库存余额加锁读（{@code IssueMapper.lockBalance}）的投影。
 * <p>出库时用它做「够不够扣」的判断，并用 {@code version} 做乐观锁更新；
 * 漏一列会让库存不足校验或并发控制静默失效。
 *
 * @param id       余额行主键
 * @param quantity 当前库存
 * @param version  版本号，更新时作为 CAS 条件
 */
public record BalanceLock(
        Long id,
        BigDecimal quantity,
        Long version) {
}
