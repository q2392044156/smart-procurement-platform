package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.procuremind.platform.pojo.row.PurchaseOrderStatus;
import com.procuremind.platform.pojo.row.ReceiptItemLock;

/**
 * 收货与库存增加的写入链路，对应 resources/mapper/ReceiptMapper.xml。
 * 顺序约定：insertIdempotency 抢幂等键 → lockOrder 锁订单 → lockOrderItem 锁订单行 → insertReceipt
 * (+Item) → upsertBatch/insertSerial → insertBalance/updateBalance（带 version）→ insertLedger →
 * completeIdempotency；任一步失败由 Service 回滚，幂等记录随事务一起消失。
 */
@Mapper
public interface ReceiptMapper {

    // 幂等三件套（insert/find/complete）与 IdempotencyMapper 语义相同，但表在收货域，故单独一套。
    int insertIdempotency(@Param("key") String key, @Param("userId") Long userId,
                          @Param("operation") String operation);

    Map<String, Object> findIdempotency(@Param("key") String key, @Param("userId") Long userId,
                                        @Param("operation") String operation);

    int completeIdempotency(@Param("key") String key, @Param("userId") Long userId,
                            @Param("operation") String operation, @Param("responseJson") String responseJson);

    // lockOrder/lockOrderItem 返回 record，是锁后判断的决策读：订单状态与行可收量都以锁住那一刻为准。
    PurchaseOrderStatus lockOrder(@Param("id") Long id);

    int countEnabledWarehouse(@Param("id") Long id);

    int insertReceipt(@Param("receiptNo") String receiptNo, @Param("purchaseOrderId") Long purchaseOrderId,
                      @Param("warehouseId") Long warehouseId, @Param("userId") Long userId,
                      @Param("receiptDate") Instant receiptDate, @Param("remark") String remark);

    Long findIdByNo(@Param("receiptNo") String receiptNo);

    ReceiptItemLock lockOrderItem(@Param("id") Long id, @Param("purchaseOrderId") Long purchaseOrderId);

    int insertReceiptItem(@Param("receiptId") Long receiptId, @Param("orderItemId") Long orderItemId,
                          @Param("materialId") Long materialId, @Param("quantity") BigDecimal quantity,
                          @Param("batchNo") String batchNo, @Param("serialJson") String serialJson,
                          @Param("userId") Long userId);

    Long lastInsertId();

    int increaseReceived(@Param("id") Long id, @Param("quantity") BigDecimal quantity, @Param("userId") Long userId);

    int upsertBatch(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                    @Param("batchNo") String batchNo, @Param("quantity") BigDecimal quantity);

    int insertSerial(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                     @Param("serialNo") String serialNo, @Param("receiptItemId") Long receiptItemId);

    int insertBalance(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                      @Param("userId") Long userId);

    Map<String, Object> findBalance(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId);

    // 带 version 做乐观锁，版本冲突即并发收货，Service 需要重试整个内层事务。
    int updateBalance(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
                      @Param("userId") Long userId, @Param("version") Object version);

    // 台账比出库侧多记一个 requestId，便于把库存变动与请求日志对上。
    int insertLedger(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                     @Param("businessId") Long businessId, @Param("quantity") BigDecimal quantity,
                     @Param("balanceAfter") BigDecimal balanceAfter, @Param("batchNo") String batchNo,
                     @Param("userId") Long userId, @Param("requestId") String requestId);

    // 统计未收满的订单行，收满才由 updateOrderStatus 把订单推进到完成。
    Long countUnfinished(@Param("purchaseOrderId") Long purchaseOrderId);

    int updateOrderStatus(@Param("id") Long id, @Param("status") String status, @Param("userId") Long userId);
}
