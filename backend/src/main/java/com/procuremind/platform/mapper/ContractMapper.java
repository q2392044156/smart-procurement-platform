package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContractMapper {

    int countPage(@Param("like") String like, @Param("state") String state);

    List<Map<String, Object>> page(@Param("like") String like, @Param("state") String state,
                                   @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findDetail(@Param("id") Long id);

    int insert(@Param("contractNo") String contractNo,
               @Param("name") String name,
               @Param("supplierId") Long supplierId,
               @Param("purchaseOrderId") Long purchaseOrderId,
               @Param("type") String type,
               @Param("amount") BigDecimal amount,
               @Param("signedDate") LocalDate signedDate,
               @Param("startDate") LocalDate startDate,
               @Param("endDate") LocalDate endDate,
               @Param("fileId") Long fileId,
               @Param("remark") String remark,
               @Param("userId") Long userId);

    Long findIdByNo(@Param("contractNo") String contractNo);

    int update(@Param("id") Long id,
               @Param("name") String name,
               @Param("supplierId") Long supplierId,
               @Param("purchaseOrderId") Long purchaseOrderId,
               @Param("type") String type,
               @Param("amount") BigDecimal amount,
               @Param("signedDate") LocalDate signedDate,
               @Param("startDate") LocalDate startDate,
               @Param("endDate") LocalDate endDate,
               @Param("fileId") Long fileId,
               @Param("remark") String remark,
               @Param("userId") Long userId);

    int activate(@Param("id") Long id, @Param("userId") Long userId);

    int terminate(@Param("id") Long id, @Param("userId") Long userId);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int expireDue();

    Map<String, Object> findStatus(@Param("id") Long id);

    int countSupplier(@Param("supplierId") Long supplierId);

    Long findOrderSupplier(@Param("purchaseOrderId") Long purchaseOrderId);
}
