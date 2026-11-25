package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.procuremind.platform.pojo.row.PurchaseOrderStatus;
import com.procuremind.platform.pojo.row.ReceiptItemLock;

@Mapper
public interface ReceiptMapper {

    int insertIdempotency(@Param("key") String key, @Param("userId") Long userId,
                          @Param("operation") String operation);

    Map<String, Object> findIdempotency(@Param("key") String key, @Param("userId") Long userId,
                                        @Param("operation") String operation);

    int completeIdempotency(@Param("key") String key, @Param("userId") Long userId,
                            @Param("operation") String operation, @Param("responseJson") String responseJson);

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

    int updateBalance(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
                      @Param("userId") Long userId, @Param("version") Object version);

    int insertLedger(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                     @Param("businessId") Long businessId, @Param("quantity") BigDecimal quantity,
                     @Param("balanceAfter") BigDecimal balanceAfter, @Param("batchNo") String batchNo,
                     @Param("userId") Long userId, @Param("requestId") String requestId);

    Long countUnfinished(@Param("purchaseOrderId") Long purchaseOrderId);

    int updateOrderStatus(@Param("id") Long id, @Param("status") String status, @Param("userId") Long userId);
}
