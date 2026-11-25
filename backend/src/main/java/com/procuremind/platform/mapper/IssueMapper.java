package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.row.BalanceLock;

/**
 * 出库单与库存余额扣减的写入链路，对应 resources/mapper/IssueMapper.xml。
 * 顺序约定：countEnabledWarehouse/findMaterial 校验 → insertIssue 建单 → lockBalance 加行锁 →
 * updateBalance（带 version）→ issueSerial/decrementBatch 扣批次或序列号 → insertLedger 记台账。
 * lockBalance 返回 BalanceLock（record），是锁后判断的决策读，必须在同一事务内用完即走。
 */
@Mapper
public interface IssueMapper {

    // 建单前校验：仓库是否启用、物资是否存在且启用，任一返回 0 或空即拒绝。
    int countEnabledWarehouse(@Param("id") Long id);

    Map<String, Object> findMaterial(@Param("id") Long id);

    // insertIssue 只写表头，数量变动体现在余额与台账里；findIdByNo 供回读单号。
    int insertIssue(@Param("issueNo") String issueNo, @Param("warehouseId") Long warehouseId,
                    @Param("materialId") Long materialId, @Param("quantity") BigDecimal quantity,
                    @Param("batchNo") String batchNo, @Param("serials") String serials,
                    @Param("purpose") String purpose, @Param("departmentId") Long departmentId,
                    @Param("userId") Long userId, @Param("issuedAt") Instant issuedAt,
                    @Param("remark") String remark);

    Long findIdByNo(@Param("issueNo") String issueNo);

    int issueSerial(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                    @Param("serialNo") String serialNo);

    int decrementBatch(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                       @Param("batchNo") String batchNo, @Param("quantity") BigDecimal quantity);

    // for update，供状态机在事务内判断余额是否足够，返回的是锁住那一刻的快照。
    BalanceLock lockBalance(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId);

    // 带 version 做乐观锁：版本不一致说明有人并发改过，Service 需重试或报冲突。
    int updateBalance(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
                      @Param("userId") Long userId, @Param("version") Object version);

    // 台账每行都写 balanceAfter，事后对账靠它而不是当前余额。
    int insertLedger(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                     @Param("businessId") Long businessId, @Param("quantity") BigDecimal quantity,
                     @Param("balanceAfter") BigDecimal balanceAfter, @Param("batchNo") String batchNo,
                     @Param("userId") Long userId);

    int countPage(@Param("like") String like);

    List<Map<String, Object>> page(@Param("like") String like, @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findDetail(@Param("id") Long id);
}
