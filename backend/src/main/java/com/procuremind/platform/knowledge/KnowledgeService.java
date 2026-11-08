package com.procuremind.platform.knowledge;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.file.FileStorageService;
import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.DataScopeService;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 知识库文档管理：元数据、版本、可见范围与下载授权。
 * 原文仍由文件模块（MinIO + fs_file_metadata）承载，本模块只负责业务语义。
 */
@Service
public class KnowledgeService {
    private static final String CATEGORY = "POLICY|PROCEDURE|CONTRACT|SUPPLIER|MANUAL|TEMPLATE|OTHER";
    private static final String VISIBILITY = "ALL|DEPARTMENT|DEPARTMENT_AND_CHILDREN|SELF|CUSTOM";
    private static final String STATUS = "DRAFT|PUBLISHED|ARCHIVED";

    private final JdbcTemplate jdbc;
    private final DataScopeService dataScope;
    private final FileStorageService files;
    private final com.procuremind.platform.file.FileContentValidator contentValidator;
    private final com.procuremind.platform.system.DocumentNumberService numbers;

    public KnowledgeService(JdbcTemplate jdbc, DataScopeService dataScope, FileStorageService files,
                            com.procuremind.platform.file.FileContentValidator contentValidator,
                            com.procuremind.platform.system.DocumentNumberService numbers) {
        this.jdbc = jdbc; this.dataScope = dataScope; this.files = files;
        this.contentValidator = contentValidator; this.numbers = numbers;
    }

    // ==================== 查询 ====================

    public Map<String, Object> list(String keyword, String category, String status, int current, int size) {
        int page = Math.max(current, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        where.add("d.deleted=0");
        if (keyword != null && !keyword.isBlank()) {
            where.add("(d.title like ? or d.code like ? or d.tags like ? or d.summary like ?)");
            String like = "%" + keyword.trim() + "%";
            args.addAll(List.of(like, like, like, like));
        }
        if (category != null && !category.isBlank()) { where.add("d.category=?"); args.add(category); }
        if (canManage()) {
            if (status != null && !status.isBlank()) { where.add("d.status=?"); args.add(status); }
        } else {
            where.add("d.status='PUBLISHED'");
        }
        Clause visible = visibilityClause();
        where.add(visible.sql());
        args.addAll(Arrays.asList(visible.args()));

        String filter = String.join(" and ", where);
        Long total = jdbc.queryForObject("select count(*) from kb_document d where " + filter, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select cast(d.id as char) id,d.code,d.title,d.category,d.summary,d.tags,
                       cast(d.owner_department_id as char) ownerDepartmentId,dep.name ownerDepartment,
                       d.visibility,d.status,d.current_version currentVersion,
                       cast(d.file_id as char) fileId,f.original_name fileName,f.size_bytes sizeBytes,
                       d.effective_from effectiveFrom,d.effective_to effectiveTo,
                       d.created_at createdAt,d.updated_at updatedAt,u.display_name createdByName,
                       (select count(*) from kb_document_acl a where a.document_id=d.id and a.subject_type='USER') userAclCount,
                       (select count(*) from kb_document_acl a where a.document_id=d.id and a.subject_type='ROLE') roleAclCount
                from kb_document d
                join fs_file_metadata f on f.id=d.file_id and f.deleted=0
                left join sys_department dep on dep.id=d.owner_department_id
                left join sys_user u on u.id=d.created_by
                where """ + " " + filter + " order by d.updated_at desc,d.id desc limit ? offset ?", pageArgs.toArray());
        long totalCount = total == null ? 0 : total;
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
        Map<String, Object> doc = jdbc.queryForMap("""
                select cast(d.id as char) id,d.code,d.title,d.category,d.summary,d.tags,
                       cast(d.owner_department_id as char) ownerDepartmentId,dep.name ownerDepartment,
                       d.visibility,d.status,d.current_version currentVersion,
                       cast(d.file_id as char) fileId,f.original_name fileName,f.content_type contentType,f.size_bytes sizeBytes,
                       d.effective_from effectiveFrom,d.effective_to effectiveTo,
                       d.created_at createdAt,d.updated_at updatedAt,u.display_name createdByName
                from kb_document d
                join fs_file_metadata f on f.id=d.file_id and f.deleted=0
                left join sys_department dep on dep.id=d.owner_department_id
                left join sys_user u on u.id=d.created_by
                where d.id=? and d.deleted=0
                """, id);
        doc.put("versions", jdbc.queryForList("""
                select cast(v.id as char) id,v.version,cast(v.file_id as char) fileId,v.file_name fileName,
                       v.change_note changeNote,v.created_at createdAt,u.display_name createdByName
                from kb_document_version v left join sys_user u on u.id=v.created_by
                where v.document_id=? order by v.version desc
                """, id));
        if (canManage()) {
            doc.put("acl", jdbc.queryForList("select cast(id as char) id,subject_type subjectType,subject_key subjectKey from kb_document_acl where document_id=? order by subject_type,subject_key", id));
        }
        return doc;
    }

    public Map<String, Object> download(Long id) {
        Map<String, Object> row = loadReadable(id);
        return files.presignRead(Long.valueOf(row.get("fileId").toString()));
    }

    /** 检索服务使用的已发布内容，忽略维护权限，只按可见范围放行。 */
    public Map<String, Object> publishedContent(Long id) {
        Clause visible = visibilityClause();
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.addAll(Arrays.asList(visible.args()));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(d.file_id as char) fileId from kb_document d
                where d.id=? and d.deleted=0 and d.status='PUBLISHED' and """ + " " + visible.sql(), args.toArray());
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在或不在当前可见范围内");
        return files.presignRead(Long.valueOf(rows.getFirst().get("fileId").toString()));
    }

    /** 当前身份可见的已发布文档，供检索前过滤。 */
    public List<Map<String, Object>> visibleDocumentIds() {
        Clause visible = visibilityClause();
        return jdbc.queryForList("""
                select cast(d.id as char) id,d.title,d.category,d.current_version currentVersion,d.updated_at updatedAt
                from kb_document d
                where d.deleted=0 and d.status='PUBLISHED' and """ + " " + visible.sql() + " order by d.id", visible.args());
    }

    /** 指定时间之后变化的文档元数据（含归档与删除），供增量重建索引。 */
    public List<Map<String, Object>> changedSince(java.time.Instant since) {
        return jdbc.queryForList("""
                select cast(id as char) id,status,deleted,current_version currentVersion,updated_at updatedAt
                from kb_document where updated_at >= ? order by updated_at,id
                """, java.sql.Timestamp.from(since));
    }

    public Map<String, Object> presignUpload(UploadRequest request) {
        requireManage();
        return files.presignUpload(request.fileName().trim(), request.contentType(), request.size());
    }

    // ==================== 写入 ====================

    @Transactional
    public Long create(CreateRequest request) {
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

        jdbc.update("""
                insert into kb_document(code,title,category,summary,tags,owner_department_id,visibility,status,current_version,
                    file_id,effective_from,effective_to,created_by,updated_by)
                values(?,?,?,?,?,?,?,'DRAFT',1,?,?,?,?,?)
                """, code, request.title().trim(), request.category(), blank(request.summary()), blank(request.tags()),
                department, visibility, request.fileId(), request.effectiveFrom(), request.effectiveTo(), user.id(), user.id());
        Long id = jdbc.queryForObject("select id from kb_document where code=?", Long.class, code);
        insertVersion(id, 1, request.fileId(), request.changeNote(), user.id());
        replaceAcl(id, visibility, request.acl(), user.id());
        return id;
    }

    @Transactional
    public void update(Long id, UpdateRequest request) {
        requireManage();
        Map<String, Object> row = requireDocument(id);
        if ("ARCHIVED".equals(row.get("status"))) throw conflict("已归档文档不能修改，请先重新发布");
        validatePeriod(request.effectiveFrom(), request.effectiveTo());
        requireDepartment(request.ownerDepartmentId() == null ? ((Number) row.get("ownerDepartmentId")).longValue() : request.ownerDepartmentId());
        String visibility = request.visibility();
        if ("CUSTOM".equals(visibility)) validateAcl(request.acl());
        Long department = request.ownerDepartmentId() == null ? ((Number) row.get("ownerDepartmentId")).longValue() : request.ownerDepartmentId();
        jdbc.update("""
                update kb_document set title=?,category=?,summary=?,tags=?,owner_department_id=?,visibility=?,
                    effective_from=?,effective_to=?,updated_by=? where id=? and deleted=0
                """, request.title().trim(), request.category(), blank(request.summary()), blank(request.tags()),
                department, visibility, request.effectiveFrom(), request.effectiveTo(), SecurityUtils.currentUser().id(), id);
        replaceAcl(id, visibility, request.acl(), SecurityUtils.currentUser().id());
    }

    @Transactional
    public int addVersion(Long id, VersionRequest request) {
        requireManage();
        Map<String, Object> row = requireDocument(id);
        if ("ARCHIVED".equals(row.get("status"))) throw conflict("已归档文档不能新增版本，请先重新发布");
        requireFile(request.fileId());
        contentValidator.verify(request.fileId());
        int next = ((Number) row.get("currentVersion")).intValue() + 1;
        Long user = SecurityUtils.currentUser().id();
        insertVersion(id, next, request.fileId(), request.changeNote(), user);
        jdbc.update("update kb_document set file_id=?,current_version=?,updated_by=? where id=? and deleted=0", request.fileId(), next, user, id);
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
        jdbc.update("update kb_document set status=?,updated_by=? where id=? and deleted=0", status, SecurityUtils.currentUser().id(), id);
    }

    @Transactional
    public void delete(Long id) {
        requireManage();
        Map<String, Object> row = requireDocument(id);
        String status = row.get("status").toString();
        if (!List.of("DRAFT", "ARCHIVED").contains(status)) throw conflict("只有草稿或已归档文档可以删除，请先归档");
        Long user = SecurityUtils.currentUser().id();
        jdbc.update("update kb_document set deleted=1,updated_by=? where id=?", user, id);
        jdbc.update("delete from kb_document_acl where document_id=?", id);
    }

    // ==================== 内部实现 ====================

    private Map<String, Object> loadReadable(Long id) {
        Clause visible = visibilityClause();
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.addAll(Arrays.asList(visible.args()));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(d.id as char) id,cast(d.file_id as char) fileId,d.status,d.visibility
                from kb_document d where d.id=? and d.deleted=0 and """ + " " + visible.sql()
                + (canManage() ? "" : " and d.status='PUBLISHED'"), args.toArray());
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在或不在当前可见范围内");
        return rows.getFirst();
    }

    private Map<String, Object> requireDocument(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select cast(d.id as char) id,d.status,d.visibility,d.current_version currentVersion,
                       cast(d.owner_department_id as char) ownerDepartmentId,cast(d.file_id as char) fileId,
                       d.effective_to effectiveTo
                from kb_document d where d.id=? and d.deleted=0
                """, id);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "知识库文档不存在");
        Map<String, Object> row = rows.getFirst();
        row.put("fileId", Long.valueOf(row.get("fileId").toString()));
        row.put("ownerDepartmentId", Long.valueOf(row.get("ownerDepartmentId").toString()));
        row.put("currentVersion", Integer.valueOf(row.get("currentVersion").toString()));
        return row;
    }

    private Clause visibilityClause() {
        CurrentUser user = SecurityUtils.currentUser();
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        parts.add("d.visibility='ALL'");
        parts.add("(d.visibility='SELF' and d.created_by=?)"); args.add(user.id());
        parts.add("(d.visibility='DEPARTMENT' and d.owner_department_id=?)"); args.add(user.departmentId());
        List<Long> departments = dataScope.departmentSubtree(user.departmentId());
        parts.add("(d.visibility='DEPARTMENT_AND_CHILDREN' and d.owner_department_id in (" + placeholders(departments.size()) + "))");
        args.addAll(departments);
        StringBuilder acl = new StringBuilder("(d.visibility='CUSTOM' and exists(select 1 from kb_document_acl a where a.document_id=d.id and ((a.subject_type='USER' and a.subject_key=?)");
        args.add(user.id().toString());
        List<String> roles = new ArrayList<>(user.roles());
        if (!roles.isEmpty()) {
            acl.append(" or (a.subject_type='ROLE' and a.subject_key in (").append(placeholders(roles.size())).append("))");
            args.addAll(roles);
        }
        acl.append(")))");
        parts.add(acl.toString());
        return new Clause("(" + String.join(" or ", parts) + ")", args.toArray());
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "生效起始日期不能晚于截止日期");
        }
    }

    private void validateAcl(List<AclEntry> acl) {
        if (acl == null || acl.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "自定义可见范围至少需要指定一个用户或角色");
        for (AclEntry entry : acl) {
            if ("USER".equals(entry.subjectType())) {
                long id;
                try { id = Long.parseLong(entry.subjectKey().trim()); }
                catch (NumberFormatException e) { throw new BusinessException(HttpStatus.BAD_REQUEST, "授权用户标识不合法"); }
                if (count("select count(*) from sys_user where id=? and deleted=0", id) == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "授权用户不存在");
            } else if (count("select count(*) from sys_role where code=? and deleted=0", entry.subjectKey().trim()) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "授权角色不存在");
            }
        }
    }

    private void replaceAcl(Long documentId, String visibility, List<AclEntry> acl, Long user) {
        jdbc.update("delete from kb_document_acl where document_id=?", documentId);
        if (!"CUSTOM".equals(visibility) || acl == null) return;
        for (AclEntry entry : acl) {
            jdbc.update("insert into kb_document_acl(document_id,subject_type,subject_key,created_by) values(?,?,?,?)",
                    documentId, entry.subjectType(), entry.subjectKey().trim(), user);
        }
    }

    private void insertVersion(Long documentId, int version, Long fileId, String note, Long user) {
        String name = jdbc.queryForObject("select original_name from fs_file_metadata where id=? and deleted=0", String.class, fileId);
        jdbc.update("insert into kb_document_version(document_id,version,file_id,file_name,change_note,created_by) values(?,?,?,?,?,?)",
                documentId, version, fileId, name, blank(note), user);
    }

    private String generateCode() {
        return numbers.next("KB");
    }

    private void requireFile(Long fileId) {
        if (count("select count(*) from fs_file_metadata where id=? and deleted=0", fileId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件不存在，请先完成上传");
        }
    }

    private void requireDepartment(Long departmentId) {
        if (departmentId == null || count("select count(*) from sys_department where id=? and deleted=0", departmentId) == 0) {
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

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String placeholders(int size) { return String.join(",", java.util.Collections.nCopies(Math.max(size, 1), "?")); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (value == null) return null;
        return LocalDate.parse(value.toString());
    }

    private record Clause(String sql, Object[] args) {}

    // ==================== 请求模型 ====================

    public record CreateRequest(
            @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = CATEGORY) String category,
            @Size(max = 500) String summary,
            @Size(max = 300) String tags,
            Long ownerDepartmentId,
            @NotBlank @Pattern(regexp = VISIBILITY) String visibility,
            @NotNull @Positive Long fileId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            List<AclEntry> acl,
            @Size(max = 300) String changeNote) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = CATEGORY) String category,
            @Size(max = 500) String summary,
            @Size(max = 300) String tags,
            Long ownerDepartmentId,
            @NotBlank @Pattern(regexp = VISIBILITY) String visibility,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            List<AclEntry> acl) {}

    public record AclEntry(@NotBlank @Pattern(regexp = "USER|ROLE") String subjectType, @NotBlank @Size(max = 64) String subjectKey) {}

    public record VersionRequest(@NotNull @Positive Long fileId, @Size(max = 300) String changeNote) {}

    public record StatusRequest(@NotBlank @Pattern(regexp = STATUS) String status) {}

    public record UploadRequest(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Pattern(regexp = "application/pdf|application/msword"
                    + "|application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    + "|application/vnd.ms-excel|application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    + "|application/vnd.ms-powerpoint|application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    + "|text/plain|text/markdown") String contentType,
            @NotNull @Positive @Max(52428800) Long size) {}
}
