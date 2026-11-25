package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface IssueMapper {

    int countEnabledWarehouse(@Param("id") Long id);

    Map<String, Object> findMaterial(@Param("id") Long id);

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

    Map<String, Object> lockBalance(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId);

    int updateBalance(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
                      @Param("userId") Long userId, @Param("version") Object version);

    int insertLedger(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                     @Param("businessId") Long businessId, @Param("quantity") BigDecimal quantity,
                     @Param("balanceAfter") BigDecimal balanceAfter, @Param("batchNo") String batchNo,
                     @Param("userId") Long userId);

    int countPage(@Param("like") String like);

    List<Map<String, Object>> page(@Param("like") String like, @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findDetail(@Param("id") Long id);
}
