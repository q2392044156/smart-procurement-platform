package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.service.FileStorageService;
import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.mapper.KnowledgeMapper;
import com.procuremind.platform.service.DataScopeService;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.KnowledgeAclEntry;
import com.procuremind.platform.pojo.request.KnowledgeCreateRequest;
import com.procuremind.platform.pojo.request.KnowledgeUpdateRequest;
import com.procuremind.platform.pojo.request.KnowledgeUploadRequest;
import com.procuremind.platform.pojo.request.KnowledgeVersionRequest;

/**
 * 知识库文档管理：元数据、版本、可见范围与下载授权。
 * 原文仍由文件模块（MinIO + fs_file_metadata）承载，本模块只负责业务语义。
 */
@Service
public class KnowledgeService {
    private static final String CATEGORY = "POLICY|PROCEDURE|CONTRACT|SUPPLIER|MANUAL|TEMPLATE|OTHER";
    private static final String VISIBILITY = "ALL|DEPARTMENT|DEPARTMENT_AND_CHILDREN|SELF|CUSTOM";
    private static final String STATUS = "DRAFT|PUBLISHED|ARCHIVED";

    private final KnowledgeMapper mapper;
    private final DataScopeService dataScope;
    private final FileStorageService files;
    private final com.procuremind.platform.service.FileContentValidator contentValidator;
    private final com.procuremind.platform.service.DocumentNumberService numbers;

    public KnowledgeService(KnowledgeMapper mapper, DataScopeService dataScope, FileStorageService files,
                            com.procuremind.platform.service.FileContentValidator contentValidator,
                            com.procuremind.platform.service.DocumentNumberService numbers) {
        this.mapper = mapper; this.dataScope = dataScope; this.files = files;
        this.contentValidator = contentValidator; this.numbers = numbers;
    }

    // ==================== 查询 ====================

    public Map<String, Object> list(String keyword, String category, String status, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        boolean manage = canManage();
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedCategory = category == null || category.isBlank() ? null : category;
        String normalizedStatus = status == null || status.isBlank() ? null : status;
        Visibility visible = visibility();
        long totalCount = mapper.countDocuments(normalizedKeyword, normalizedCategory, normalizedStatus, manage,
                visible.userId(), visible.userIdKey(), visible.departmentId(),
                visible.departmentIds(), visible.roles());
        List<Map<String, Object>> records = mapper.pageDocuments(normalizedKeyword, normalizedCategory,
                normalizedStatus, manage, visible.userId(), visible.userIdKey(), visible.departmentId(),
                visible.departmentIds(), visible.roles(), pageSize, (page - 1) * pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", (int) totalCount);
        result.put("current", page);
        result.put("size", pageSize);
        result.put("pages", (int) ((totalCount + pageSize - 1) / pageSize));
        return result;
    }

    public Map<String, Object> detail(Long id) {
        loadReadable(id);
        Map<String, Object> doc = mapper.findDetail(id);
        doc.put("versions", mapper.findVersions(id));
        if (canManage()) {
            doc.put("acl", mapper.findAcl(id));
        }
        return doc;
    }

    public Map<String, Object> download(Long id) {
        Map<String, Object> row = loadReadable(id);
        return files.presignRead(Long.valueOf(row.get("fileId").toString()));
    }

    /** 检索服务使用的已发布内容，忽略维护权限，只按可见范围放行。 */
    public Map<String, Object> publishedContent(Long id) {
        Map<String, Object> row = findPublished(id);
        return files.presignRead(Long.valueOf(row.get("fileId").toString()));
    }

    /** 检索服务内部读取已发布原文，避免预签名 URL 的 host 在容器内不可达。 */
    public Map<String, Object> publishedRaw(Long id) {
        Map<String, Object> row = findPublished(id);
        return files.raw(Long.valueOf(row.get("fileId").toString()));
    }

    /** 当前身份可见的已发布文档，供检索前过滤。 */
    public List<Map<String, Object>> visibleDocumentIds() {
        Visibility visible = visibility();
        return mapper.visibleDocumentIds(visible.userId(), visible.userIdKey(), visible.departmentId(),
                visible.departmentIds(), visible.roles());
    }

    /** 指定时间之后变化的文档元数据（含归档与删除），供增量重建索引。 */
    public List<Map<String, Object>> changedSince(java.time.Instant since) {
        return mapper.changedSince(java.sql.Timestamp.from(since));
    }

    public Map<String, Object> presignUpload(KnowledgeUploadRequest request) {
        requireManage();
        return files.presignUpload(request.fileName().trim(), request.contentType(), request.size());
    }

    // ==================== 写入 ====================

    @Transactional
    public Long create(KnowledgeCreateRequest request) {
        requireManage();
        validatePeriod(request.effectiveFrom(), request.effectiveTo());
        if ("CUSTOM".equals(request.visibility())) validateAcl(request.acl());
        requireFile(request.fileId());
        contentValidator.verify(request.fileId());
        CurrentUser user = SecurityUtils.currentUser();
        Long department = request.ownerDepartmentId() == null ? user.departmentId() : request.ownerDepartmentId();
        requireDepartment(department);
        String code = request.code() == null || request.code().isBlank() ? generateCode() : request.code().trim();
        String visibility = request.visibility();
        if ("CUSTOM".equals(visibility)) validateAcl(request.acl());

        mapper.insertDocument(code, request.title().trim(), request.category(), blank(request.summary()),
                blank(request.tags()), department, visibility, request.fileId(), request.effectiveFrom(),
                request.effectiveTo(), user.id());
        Long id = mapper.findIdByCode(code);
        insertVersion(id, 1, request.fileId(), request.changeNote(), user.id());
        replaceAcl(id, visibility, request.acl(), user.id());
        return id;
    }

    @Transactional
    public void update(Long id, KnowledgeUpdateRequest request) {
        requireManage();
        Map<String, Object> row = requireDocument(id);
        if ("ARCHIVED".equals(row.get("status"))) throw conflict("已归档文档不能修改，请先重新发布");
        validatePeriod(request.effectiveFrom(), request.effectiveTo());
        requireDepartment(request.ownerDepartmentId() == null ? ((Number) row.get("ownerDepartmentId")).longValue() : request.ownerDepartmentId());
        String visibility = request.visibility();
        if ("CUSTOM".equals(visibility)) validateAcl(request.acl());
        Long department = request.ownerDepartmentId() == null ? ((Number) row.get("ownerDepartmentId")).longValue() : request.ownerDepartmentId();
        mapper.updateDocument(id, request.title().trim(), request.category(), blank(request.summary()),
                blank(request.tags()), department, visibility, request.effectiveFrom(), request.effectiveTo(),
                SecurityUtils.currentUser().id());
        replaceAcl(id, visibility, request.acl(), SecurityUtils.currentUser().id());
    }

    @Transactional
    public int addVersion(Long id, KnowledgeVersionRequest request) {
        requireManage();
        Map<String, Object> row = requireDocument(id);
        if ("ARCHIVED".equals(row.get("status"))) throw conflict("已归档文档不能新增版本，请先重新发布");
        requireFile(request.fileId());
        contentValidator.verify(request.fileId());
        int next = ((Number) row.get("currentVersion")).intValue() + 1;
        Long user = SecurityUtils.currentUser().id();
        insertVersion(id, next, request.fileId(), request.changeNote(), user);
        mapper.updateFile(id, request.fileId(), next, user);
        return next;
    }

    @Transactional
    public void changeStatus(Long id, String status) {
        requireManage();
        if (!List.of("PUBLISHED", "ARCHIVED", "DRAFT").contains(status)) throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的文档状态");
        Map<String, Object> row = requireDocument(id);
        String from = row.get("status").toString();
        if (from.equals(status)) return;
        boolean allowed = ("DRAFT".equals(from) && "PUBLISHED".equals(status))
                || ("PUBLISHED".equals(from) && "ARCHIVED".equals(status))
                || ("ARCHIVED".equals(from) && "PUBLISHED".equals(status));
        if (!allowed) throw conflict("不允许的状态变更：" + from + " → " + status);
        if ("PUBLISHED".equals(status)) {
            LocalDate effectiveTo = toLocalDate(row.get("effectiveTo"));
            if (effectiveTo != null && effectiveTo.isBefore(LocalDate.now())) throw conflict("文档已超过有效期，不能发布");
            requireFile(((Number) row.get("fileId")).longValue());
        }
        mapper.updateStatus(id, status, SecurityUtils.currentUser().id());
    }

    @Transactional
    public void delete(Long id) {
        requireManage();
        Map<String, Object> row = requireDocument(id);
        String status = row.get("status").toString();
        if (!List.of("DRAFT", "ARCHIVED").contains(status)) throw conflict("只有草稿或已归档文档可以删除，请先归档");
        Long user = SecurityUtils.currentUser().id();
        mapper.softDelete(id, user);
        mapper.deleteAcl(id);
    }

    // ==================== 内部实现 ====================

    private Map<String, Object> loadReadable(Long id) {
        Visibility visible = visibility();
        Map<String, Object> row = mapper.findReadable(id, canManage(), visible.userId(), visible.userIdKey(),
                visible.departmentId(), visible.departmentIds(), visible.roles());
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在或不在当前可见范围内");
        return row;
    }

    /** 已发布且可见；否则按原文案抛 404。 */
    private Map<String, Object> findPublished(Long id) {
        Visibility visible = visibility();
        Map<String, Object> row = mapper.findPublishedFile(id, visible.userId(), visible.userIdKey(),
                visible.departmentId(), visible.departmentIds(), visible.roles());
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在或不在当前可见范围内");
        return row;
    }

    private Map<String, Object> requireDocument(Long id) {
        Map<String, Object> row = mapper.findManagedDocument(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "知识库文档不存在");
        row.put("fileId", Long.valueOf(row.get("fileId").toString()));
        row.put("ownerDepartmentId", Long.valueOf(row.get("ownerDepartmentId").toString()));
        row.put("currentVersion", Integer.valueOf(row.get("currentVersion").toString()));
        return row;
    }

    /** 可见范围所需的身份参数：departmentIds 与 roles 交给 XML 的 foreach 绑定。 */
    private Visibility visibility() {
        CurrentUser user = SecurityUtils.currentUser();
        return new Visibility(user.id(), user.id().toString(), user.departmentId(),
                dataScope.departmentSubtree(user.departmentId()), new ArrayList<>(user.roles()));
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "生效起始日期不能晚于截止日期");
        }
    }

    private void validateAcl(List<KnowledgeAclEntry> acl) {
        if (acl == null || acl.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "自定义可见范围至少需要指定一个用户或角色");
        for (KnowledgeAclEntry entry : acl) {
            if ("USER".equals(entry.subjectType())) {
                long id;
                try { id = Long.parseLong(entry.subjectKey().trim()); }
                catch (NumberFormatException e) { throw new BusinessException(HttpStatus.BAD_REQUEST, "授权用户标识不合法"); }
                if (mapper.countUser(id) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "授权用户不存在");
            } else if (mapper.countRole(entry.subjectKey().trim()) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "授权角色不存在");
            }
        }
    }

    private void replaceAcl(Long documentId, String visibility, List<KnowledgeAclEntry> acl, Long user) {
        mapper.deleteAcl(documentId);
        if (!"CUSTOM".equals(visibility) || acl == null) return;
        for (KnowledgeAclEntry entry : acl) {
            mapper.insertAcl(documentId, entry.subjectType(), entry.subjectKey().trim(), user);
        }
    }

    private void insertVersion(Long documentId, int version, Long fileId, String note, Long user) {
        String name = mapper.findFileName(fileId);
        mapper.insertVersion(documentId, version, fileId, name, blank(note), user);
    }

    private String generateCode() {
        return numbers.next("KB");
    }

    private void requireFile(Long fileId) {
        if (mapper.countFile(fileId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件不存在，请先完成上传");
        }
    }

    private void requireDepartment(Long departmentId) {
        if (departmentId == null || mapper.countDepartment(departmentId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "归属部门不存在");
        }
    }

    private void requireManage() {
        if (!canManage()) throw new BusinessException(HttpStatus.FORBIDDEN, "无权维护知识库");
    }

    private boolean canManage() {
        CurrentUser user = SecurityUtils.currentUser();
        return user.roles().contains("ADMIN") || user.permissions().contains("kb:manage");
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (value == null) return null;
        return LocalDate.parse(value.toString());
    }

    private record Visibility(Long userId, String userIdKey, Long departmentId,
                              List<Long> departmentIds, List<String> roles) {}

    // ==================== 请求模型 ====================

    

    

    

    

    

    
}
