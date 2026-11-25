package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.row.RequisitionLock;

/** 采购申请的读写映射。SQL 全部在 resources/mapper/RequisitionMapper.xml。 */
@Mapper
public interface RequisitionMapper {

    List<Map<String, Object>> list(@Param("scope") String scope);

    long countPage(@Param("scope") String scope, @Param("keyword") String keyword, @Param("status") String status);

    List<Map<String, Object>> page(@Param("scope") String scope, @Param("keyword") String keyword,
                                   @Param("status") String status, @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findDetail(@Param("id") Long id);

    List<Map<String, Object>> findItems(@Param("id") Long id);

    /** 行锁读取，供提交/取消/删除前判定状态。 */
    RequisitionLock lockById(@Param("id") Long id);

    int insertHeader(@Param("requisitionNo") String requisitionNo, @Param("departmentId") Long departmentId,
                     @Param("applicantId") Long applicantId, @Param("requiredDate") LocalDate requiredDate,
                     @Param("purpose") String purpose, @Param("budgetAmount") BigDecimal budgetAmount,
                     @Param("userId") Long userId);

    Long findIdByNo(@Param("requisitionNo") String requisitionNo);

    int insertItem(@Param("requisitionId") Long requisitionId, @Param("materialId") Long materialId,
                   @Param("quantity") BigDecimal quantity, @Param("estimatedUnitPrice") BigDecimal estimatedUnitPrice,
                   @Param("remark") String remark, @Param("userId") Long userId);

    int updateHeader(@Param("id") Long id, @Param("requiredDate") LocalDate requiredDate,
                     @Param("purpose") String purpose, @Param("budgetAmount") BigDecimal budgetAmount,
                     @Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("processInstanceId") String processInstanceId, @Param("userId") Long userId);

    int cancel(@Param("id") Long id, @Param("reason") String reason, @Param("userId") Long userId);

    int softDeleteItems(@Param("id") Long id, @Param("userId") Long userId);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int countEnabledMaterial(@Param("materialId") Long materialId);
}
