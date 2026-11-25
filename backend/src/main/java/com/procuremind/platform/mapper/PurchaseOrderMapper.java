package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.row.PurchaseOrderLock;
import com.procuremind.platform.pojo.row.RequisitionStatus;

@Mapper
public interface PurchaseOrderMapper {

    List<Map<String, Object>> list(@Param("scope") String scope);

    long countPage(@Param("scope") String scope, @Param("keyword") String keyword, @Param("status") String status);

    List<Map<String, Object>> page(@Param("scope") String scope, @Param("keyword") String keyword,
                                   @Param("status") String status, @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findDetail(@Param("id") Long id);

    List<Map<String, Object>> findItems(@Param("id") Long id);

    RequisitionStatus lockRequisition(@Param("id") Long id);

    List<Map<String, Object>> findRequisitionItems(@Param("requisitionId") Long requisitionId);

    int countQualifiedSupplier(@Param("supplierId") Long supplierId);

    Long findDefaultSupplierId();

    void insertHeader(@Param("orderNo") String orderNo,
                      @Param("requisitionId") Long requisitionId,
                      @Param("supplierId") Long supplierId,
                      @Param("purchaserId") Long purchaserId,
                      @Param("deliveryDate") LocalDate deliveryDate,
                      @Param("taxRate") BigDecimal taxRate,
                      @Param("totalAmount") BigDecimal totalAmount,
                      @Param("terms") String terms,
                      @Param("userId") Long userId);

    Long findIdByNo(@Param("orderNo") String orderNo);

    void insertItem(@Param("orderId") Long orderId,
                    @Param("materialId") Long materialId,
                    @Param("quantity") BigDecimal quantity,
                    @Param("unitPrice") BigDecimal unitPrice,
                    @Param("taxRate") BigDecimal taxRate,
                    @Param("remark") String remark,
                    @Param("userId") Long userId);

    void markRequisitionConverted(@Param("requisitionId") Long requisitionId, @Param("userId") Long userId);

    PurchaseOrderLock lockById(@Param("id") Long id);

    List<Map<String, Object>> findItemQuantities(@Param("orderId") Long orderId);

    void updateHeader(@Param("id") Long id,
                      @Param("supplierId") Long supplierId,
                      @Param("deliveryDate") LocalDate deliveryDate,
                      @Param("taxRate") BigDecimal taxRate,
                      @Param("totalAmount") BigDecimal totalAmount,
                      @Param("terms") String terms,
                      @Param("userId") Long userId);

    void updateDirectHeader(@Param("id") Long id,
                            @Param("supplierId") Long supplierId,
                            @Param("deliveryDate") LocalDate deliveryDate,
                            @Param("taxRate") BigDecimal taxRate,
                            @Param("totalAmount") BigDecimal totalAmount,
                            @Param("terms") String terms,
                            @Param("userId") Long userId);

    void updateItemPrice(@Param("id") Long id,
                         @Param("orderId") Long orderId,
                         @Param("unitPrice") BigDecimal unitPrice,
                         @Param("taxRate") BigDecimal taxRate,
                         @Param("remark") String remark,
                         @Param("userId") Long userId);

    void updateStatus(@Param("id") Long id, @Param("status") String status,
                      @Param("processInstanceId") String processInstanceId, @Param("userId") Long userId);

    void cancel(@Param("id") Long id, @Param("reason") String reason, @Param("userId") Long userId);

    void closeOrder(@Param("id") Long id, @Param("userId") Long userId);

    void softDeleteItems(@Param("orderId") Long orderId, @Param("userId") Long userId);

    void softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int countReceipts(@Param("orderId") Long orderId);

    int countActiveOrdersByRequisition(@Param("requisitionId") Long requisitionId);

    int restoreRequisition(@Param("requisitionId") Long requisitionId, @Param("userId") Long userId);

    List<Map<String, Object>> priceHistory(@Param("materialId") Long materialId, @Param("scope") String scope);

    int countEnabledMaterial(@Param("materialId") Long materialId);
}
