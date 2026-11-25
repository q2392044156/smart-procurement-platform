package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 供应商档案及其联系人/资质/绩效的读写映射。SQL 全部在 resources/mapper/SupplierMapper.xml。 */
@Mapper
public interface SupplierMapper {

    List<Map<String, Object>> list(@Param("keyword") String keyword);

    long countPage(@Param("keyword") String keyword, @Param("status") String status);

    List<Map<String, Object>> page(@Param("keyword") String keyword, @Param("status") String status,
                                   @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findById(@Param("id") Long id);

    List<Map<String, Object>> findContacts(@Param("supplierId") Long supplierId);

    List<Map<String, Object>> findQualifications(@Param("supplierId") Long supplierId);

    List<Map<String, Object>> findEvaluations(@Param("supplierId") Long supplierId);

    /** 主联系人快照，供回填供应商档案的 contact_name/contact_phone/email。 */
    Map<String, Object> findPrimaryContactSnapshot(@Param("supplierId") Long supplierId);

    /** 资质附件的上传人，返回列标签为 uploader_id。 */
    Map<String, Object> findFileUploader(@Param("fileId") Long fileId);

    List<Long> findFirstContactId(@Param("supplierId") Long supplierId);

    Long findIdByCode(@Param("code") String code);

    Long findMaxContactId(@Param("supplierId") Long supplierId);

    Long findMaxQualificationId(@Param("supplierId") Long supplierId, @Param("name") String name);

    Long findMaxEvaluationId(@Param("supplierId") Long supplierId, @Param("period") String period);

    int countOrdersBySupplier(@Param("supplierId") Long supplierId);

    int countContacts(@Param("supplierId") Long supplierId);

    int countPrimaryContacts(@Param("supplierId") Long supplierId);

    int countValidQualification(@Param("supplierId") Long supplierId);

    int countCreditCode(@Param("unifiedCreditCode") String unifiedCreditCode);

    int countCreditCodeExcluding(@Param("unifiedCreditCode") String unifiedCreditCode, @Param("id") Long id);

    int countQualificationName(@Param("supplierId") Long supplierId, @Param("name") String name);

    int countQualificationNameExcluding(@Param("supplierId") Long supplierId, @Param("name") String name,
                                        @Param("id") Long id);

    int countEvaluationPeriod(@Param("supplierId") Long supplierId, @Param("period") String period);

    int countEvaluationPeriodExcluding(@Param("supplierId") Long supplierId, @Param("period") String period,
                                       @Param("id") Long id);

    /** 子表归属校验。table 只接收服务内写死的三张子表名，不来自请求参数。 */
    int countContact(@Param("id") Long id, @Param("supplierId") Long supplierId);

    int countQualification(@Param("id") Long id, @Param("supplierId") Long supplierId);

    int countEvaluation(@Param("id") Long id, @Param("supplierId") Long supplierId);

    int insertSupplier(@Param("code") String code, @Param("name") String name,
                       @Param("unifiedCreditCode") String unifiedCreditCode, @Param("level") String level,
                       @Param("status") String status, @Param("contactName") String contactName,
                       @Param("contactPhone") String contactPhone, @Param("email") String email,
                       @Param("address") String address, @Param("userId") Long userId);

    int updateSupplier(@Param("id") Long id, @Param("name") String name,
                       @Param("unifiedCreditCode") String unifiedCreditCode, @Param("level") String level,
                       @Param("status") String status, @Param("address") String address,
                       @Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("userId") Long userId);

    int softDeleteSupplier(@Param("id") Long id, @Param("userId") Long userId);

    int softDeleteContacts(@Param("supplierId") Long supplierId, @Param("userId") Long userId);

    int softDeleteQualifications(@Param("supplierId") Long supplierId, @Param("userId") Long userId);

    int softDeleteEvaluations(@Param("supplierId") Long supplierId, @Param("userId") Long userId);

    int insertContact(@Param("supplierId") Long supplierId, @Param("name") String name,
                      @Param("position") String position, @Param("phone") String phone,
                      @Param("email") String email, @Param("primaryContact") boolean primaryContact,
                      @Param("userId") Long userId);

    int updateContact(@Param("id") Long id, @Param("supplierId") Long supplierId, @Param("name") String name,
                      @Param("position") String position, @Param("phone") String phone,
                      @Param("email") String email, @Param("primaryContact") boolean primaryContact,
                      @Param("userId") Long userId);

    int softDeleteContact(@Param("id") Long id, @Param("supplierId") Long supplierId, @Param("userId") Long userId);

    int clearPrimary(@Param("supplierId") Long supplierId, @Param("userId") Long userId);

    int promoteFirstContactToPrimary(@Param("userId") Long userId, @Param("supplierId") Long supplierId);

    int updatePrimaryContactSnapshot(@Param("id") Long id, @Param("name") String name,
                                     @Param("phone") String phone, @Param("email") String email,
                                     @Param("userId") Long userId);

    int clearSupplierContactSnapshot(@Param("userId") Long userId, @Param("supplierId") Long supplierId);

    int updateSupplierContactSnapshot(@Param("supplierId") Long supplierId, @Param("contactName") String contactName,
                                      @Param("contactPhone") String contactPhone, @Param("email") String email,
                                      @Param("userId") Long userId);

    int insertQualification(@Param("supplierId") Long supplierId, @Param("name") String name,
                            @Param("fileId") Long fileId, @Param("validFrom") LocalDate validFrom,
                            @Param("validUntil") LocalDate validUntil, @Param("status") String status,
                            @Param("userId") Long userId);

    int updateQualification(@Param("id") Long id, @Param("supplierId") Long supplierId, @Param("name") String name,
                            @Param("fileId") Long fileId, @Param("validFrom") LocalDate validFrom,
                            @Param("validUntil") LocalDate validUntil, @Param("status") String status,
                            @Param("userId") Long userId);

    int softDeleteQualification(@Param("id") Long id, @Param("supplierId") Long supplierId,
                                @Param("userId") Long userId);

    int insertEvaluation(@Param("supplierId") Long supplierId, @Param("period") String period,
                         @Param("qualityScore") BigDecimal qualityScore, @Param("deliveryScore") BigDecimal deliveryScore,
                         @Param("serviceScore") BigDecimal serviceScore, @Param("totalScore") BigDecimal totalScore,
                         @Param("comment") String comment, @Param("userId") Long userId);

    int updateEvaluation(@Param("id") Long id, @Param("supplierId") Long supplierId, @Param("period") String period,
                         @Param("qualityScore") BigDecimal qualityScore, @Param("deliveryScore") BigDecimal deliveryScore,
                         @Param("serviceScore") BigDecimal serviceScore, @Param("totalScore") BigDecimal totalScore,
                         @Param("comment") String comment, @Param("userId") Long userId);

    int softDeleteEvaluation(@Param("id") Long id, @Param("supplierId") Long supplierId, @Param("userId") Long userId);

    int recalculateScore(@Param("supplierId") Long supplierId, @Param("userId") Long userId);
}
