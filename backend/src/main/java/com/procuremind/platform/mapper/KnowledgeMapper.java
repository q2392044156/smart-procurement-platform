package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档、版本与授权明细的读写映射。SQL 全部在 resources/mapper/KnowledgeMapper.xml。
 *
 * <p>可见性片断需要 userId（created_by 等 bigint 列）与 userIdKey（kb_document_acl.subject_key
 * 是 varchar，按原实现以字符串绑定）两个形参；部门子树与角色用集合传入，由 XML 的 foreach 绑定。</p>
 * <p>写路径（insertDocument/insertVersion/updateStatus/deleteAcl/insertAcl）不做可见性过滤，由 Service
 * 先查 findReadable 再写；countUser/countRole/countDepartment/countFile 是授权对象与文件的存在性校验。</p>
 */
@Mapper
public interface KnowledgeMapper {

    long countDocuments(@Param("keyword") String keyword, @Param("category") String category,
                        @Param("status") String status, @Param("manage") boolean manage,
                        @Param("userId") Long userId, @Param("userIdKey") String userIdKey,
                        @Param("departmentId") Long departmentId,
                        @Param("departmentIds") List<Long> departmentIds,
                        @Param("roles") List<String> roles);

    List<Map<String, Object>> pageDocuments(@Param("keyword") String keyword, @Param("category") String category,
                                            @Param("status") String status, @Param("manage") boolean manage,
                                            @Param("userId") Long userId, @Param("userIdKey") String userIdKey,
                                            @Param("departmentId") Long departmentId,
                                            @Param("departmentIds") List<Long> departmentIds,
                                            @Param("roles") List<String> roles,
                                            @Param("size") int size, @Param("offset") int offset);

    // findDetail/findVersions/findAcl 是维护路径的读取，不做可见性过滤，调用方必须是管理角色。
    Map<String, Object> findDetail(@Param("id") Long id);

    List<Map<String, Object>> findVersions(@Param("id") Long id);

    List<Map<String, Object>> findAcl(@Param("id") Long id);

    /** 按可见范围读取一行；维护者才允许看到非发布状态。 */
    Map<String, Object> findReadable(@Param("id") Long id, @Param("manage") boolean manage,
                                     @Param("userId") Long userId, @Param("userIdKey") String userIdKey,
                                     @Param("departmentId") Long departmentId,
                                     @Param("departmentIds") List<Long> departmentIds,
                                     @Param("roles") List<String> roles);

    /** 检索服务用：已发布且在当前身份可见范围内时返回文件 id。 */
    Map<String, Object> findPublishedFile(@Param("id") Long id,
                                          @Param("userId") Long userId, @Param("userIdKey") String userIdKey,
                                          @Param("departmentId") Long departmentId,
                                          @Param("departmentIds") List<Long> departmentIds,
                                          @Param("roles") List<String> roles);

    List<Map<String, Object>> visibleDocumentIds(@Param("userId") Long userId, @Param("userIdKey") String userIdKey,
                                                 @Param("departmentId") Long departmentId,
                                                 @Param("departmentIds") List<Long> departmentIds,
                                                 @Param("roles") List<String> roles);

    // 增量重建索引用：返回自 since 起有变动的文档（含归档与删除）。
    List<Map<String, Object>> changedSince(@Param("since") Timestamp since);

    /** 维护路径的读取，不做可见性过滤。 */
    Map<String, Object> findManagedDocument(@Param("id") Long id);

    Long findIdByCode(@Param("code") String code);

    String findFileName(@Param("fileId") Long fileId);

    int insertDocument(@Param("code") String code, @Param("title") String title,
                       @Param("category") String category, @Param("summary") String summary,
                       @Param("tags") String tags, @Param("ownerDepartmentId") Long ownerDepartmentId,
                       @Param("visibility") String visibility, @Param("fileId") Long fileId,
                       @Param("effectiveFrom") LocalDate effectiveFrom,
                       @Param("effectiveTo") LocalDate effectiveTo, @Param("userId") Long userId);

    int insertVersion(@Param("documentId") Long documentId, @Param("version") int version,
                      @Param("fileId") Long fileId, @Param("fileName") String fileName,
                      @Param("note") String note, @Param("userId") Long userId);

    int updateDocument(@Param("id") Long id, @Param("title") String title,
                       @Param("category") String category, @Param("summary") String summary,
                       @Param("tags") String tags, @Param("ownerDepartmentId") Long ownerDepartmentId,
                       @Param("visibility") String visibility,
                       @Param("effectiveFrom") LocalDate effectiveFrom,
                       @Param("effectiveTo") LocalDate effectiveTo, @Param("userId") Long userId);

    // updateFile 同时刷新 current_version，与 insertVersion 配对，避免两处版本号错位。
    int updateFile(@Param("id") Long id, @Param("fileId") Long fileId,
                   @Param("currentVersion") int currentVersion, @Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("userId") Long userId);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    int deleteAcl(@Param("documentId") Long documentId);

    int insertAcl(@Param("documentId") Long documentId, @Param("subjectType") String subjectType,
                  @Param("subjectKey") String subjectKey, @Param("userId") Long userId);

    int countUser(@Param("userId") Long userId);

    int countRole(@Param("code") String code);

    int countFile(@Param("fileId") Long fileId);

    int countDepartment(@Param("departmentId") Long departmentId);
}
