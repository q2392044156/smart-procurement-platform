package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowMapper {

    int updateRequisitionStatus(@Param("id") Long id, @Param("status") String status,
                                @Param("reason") String reason, @Param("userId") Long userId);

    int updatePurchaseOrderStatus(@Param("id") Long id, @Param("status") String status,
                                  @Param("userId") Long userId);
}
