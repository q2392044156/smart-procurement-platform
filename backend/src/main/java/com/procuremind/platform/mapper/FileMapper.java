package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FileMapper {

    int insertMetadata(@Param("originalName") String originalName,
                       @Param("objectKey") String objectKey,
                       @Param("contentType") String contentType,
                       @Param("size") Long size,
                       @Param("bucket") String bucket,
                       @Param("uploaderId") Long uploaderId);

    Long findIdByObjectKey(@Param("objectKey") String objectKey);

    Map<String, Object> findForRead(@Param("id") Long id);

    Map<String, Object> findForDownload(@Param("id") Long id);

    Map<String, Object> findForRaw(@Param("id") Long id);

    Map<String, Object> findForDelete(@Param("id") Long id);

    Map<String, Object> findForValidation(@Param("id") Long id);

    int softDelete(@Param("id") Long id);

    int countSupplierQualification(@Param("fileId") Long fileId);

    Long findUploaderId(@Param("fileId") Long fileId);

    // ---- 业务附件 ----

    List<Map<String, Object>> listAttachments(@Param("businessType") String businessType,
                                              @Param("businessId") Long businessId);

    int countAttachment(@Param("businessType") String businessType,
                        @Param("businessId") Long businessId,
                        @Param("fileId") Long fileId);

    int insertAttachment(@Param("businessType") String businessType,
                         @Param("businessId") Long businessId,
                         @Param("fileId") Long fileId,
                         @Param("category") String category,
                         @Param("note") String note,
                         @Param("userId") Long userId);

    Long findMaxAttachmentId(@Param("businessType") String businessType,
                             @Param("businessId") Long businessId);

    Map<String, Object> findAttachment(@Param("id") Long id);

    int softDeleteAttachment(@Param("id") Long id, @Param("userId") Long userId);

    int unlinkBusiness(@Param("businessType") String businessType,
                       @Param("businessId") Long businessId,
                       @Param("userId") Long userId);

    int countByFile(@Param("fileId") Long fileId);

    List<Map<String, Object>> listByFile(@Param("fileId") Long fileId);

    int countWritableRequisition(@Param("id") Long id, @Param("userId") Long userId,
                                 @Param("admin") int admin);

    int countWritablePurchaseOrder(@Param("id") Long id, @Param("userId") Long userId,
                                   @Param("admin") int admin);
}
