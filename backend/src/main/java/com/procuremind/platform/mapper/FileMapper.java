package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 文件元数据与业务附件的读写映射，对应 resources/mapper/FileMapper.xml；无行锁、无 record 决策读。
 * 五个 findFor* 是同一张表的不同读法（读 / 下载 / 原文 / 删除 / 校验），各自 JOIN 不同的引用表来判定归属，
 * 因此不要合并：调用方按用途选，选错就会漏掉那一层权限判断。
 */
@Mapper
public interface FileMapper {

    // 上传落库：objectKey 唯一，size 以字节记，bucket 记录存储桶名。
    int insertMetadata(@Param("originalName") String originalName,
                       @Param("objectKey") String objectKey,
                       @Param("contentType") String contentType,
                       @Param("size") Long size,
                       @Param("bucket") String bucket,
                       @Param("uploaderId") Long uploaderId);

    Long findIdByObjectKey(@Param("objectKey") String objectKey);

    // 五个 findFor*：read 回显元数据；download 校验上传人/引用；raw 供内部读取；delete 判定能否删；
    // validation 只取校验所需的 size 与 contentType。返回 null 表示不存在或已删除。
    Map<String, Object> findForRead(@Param("id") Long id);

    Map<String, Object> findForDownload(@Param("id") Long id);

    Map<String, Object> findForRaw(@Param("id") Long id);

    Map<String, Object> findForDelete(@Param("id") Long id);

    Map<String, Object> findForValidation(@Param("id") Long id);

    // softDelete 与 countSupplierQualification/findUploaderId 一起构成“能否删除”的判断材料。
    int softDelete(@Param("id") Long id);

    int countSupplierQualification(@Param("fileId") Long fileId);

    Long findUploaderId(@Param("fileId") Long fileId);

    // ---- 业务附件 ----

    // 附件区以 businessType+businessId 为聚合键：列表、去重校验、写入、解绑都按这个键；
    // findMaxAttachmentId 取当前最大附件 id，unlinkBusiness 在业务单据删除时批量解绑。
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

    // 可写性校验：admin=1 时跳过归属条件，返回 0 即当前用户不可写该单据。
    int countWritableRequisition(@Param("id") Long id, @Param("userId") Long userId,
                                 @Param("admin") int admin);

    int countWritablePurchaseOrder(@Param("id") Long id, @Param("userId") Long userId,
                                   @Param("admin") int admin);
}
