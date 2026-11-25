package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.row.PurchaseOrderLock;
import com.procuremind.platform.pojo.row.RequisitionStatus;

/**
 * 采购订单的读写映射，对应 resources/mapper/PurchaseOrderMapper.xml；SQL 全在 XML，本接口只声明签名。
 * 两个决策读返回 record：lockRequisition（转单前锁申请，确认状态未变）、lockById（状态流转前锁订单）。
 * 列表/分页/价格历史都带 scope 片断做行级过滤；softDelete* 与 restoreRequisition 成对，删单要能回退申请。
 */
@Mapper
public interface PurchaseOrderMapper {

    // list/countPage/page 共用同一套过滤条件，scope 为空表示不过滤（由 DataScopeService 保证非空）。
    List<Map<String, Object>> list(@Param("scope") String scope);

    long countPage(@Param("scope") String scope, @Param("keyword") String keyword, @Param("status") String status);

    List<Map<String, Object>> page(@Param("scope") String scope, @Param("keyword") String keyword,
                                   @Param("status") String status, @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findDetail(@Param("id") Long id);

    List<Map<String, Object>> findItems(@Param("id") Long id);

    // 转单前锁申请并读状态：只有已审批且未转换的申请才允许转单。
    RequisitionStatus lockRequisition(@Param("id") Long id);

    List<Map<String, Object>> findRequisitionItems(@Param("requisitionId") Long requisitionId);

    // countQualifiedSupplier 校验供应商仍合格；findDefaultSupplierId 提供未指定时的兜底供应商。
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

    // 把申请置为已转单；restoreRequisition 在删单时回退，两者必须与订单写入同事务。
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

    // 删改前的引用与占用校验：已有收货、或同申请上还有其他活跃订单时都不允许删。
    int countReceipts(@Param("orderId") Long orderId);

    int countActiveOrdersByRequisition(@Param("requisitionId") Long requisitionId);

    int restoreRequisition(@Param("requisitionId") Long requisitionId, @Param("userId") Long userId);

    List<Map<String, Object>> priceHistory(@Param("materialId") Long materialId, @Param("scope") String scope);

    int countEnabledMaterial(@Param("materialId") Long materialId);
}
