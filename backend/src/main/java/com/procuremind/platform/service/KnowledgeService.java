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
 *
 * <p>状态机：DRAFT → PUBLISHED ⇄ ARCHIVED，只允许这三条边；归档态不可修改、不可新增版本，
 * 删除只对 DRAFT/ARCHIVED 开放且是软删。维护权限为 ADMIN 或 kb:manage；检索侧只认 PUBLISHED。
 * 可见范围（ALL/DEPARTMENT/DEPARTMENT_AND_CHILDREN/SELF/CUSTOM）直接拼进 SQL 过滤，不在内存里筛。
 * 本类不保存文件字节、不做全文检索、不判断文件内容安全性。</p>
 */
@Service
public class KnowledgeService {
    /** 文档分类白名单，与请求模型上的 @Pattern 保持一致，新增分类需同步两处。 */
    private static final String CATEGORY = "POLICY|PROCEDURE|CONTRACT|SUPPLIER|MANUAL|TEMPLATE|OTHER";
    /** 可见范围枚举：部门、部门含下级、仅本人、自定义 ACL 四类之外再加全员。 */
    private static final String VISIBILITY = "ALL|DEPARTMENT|DEPARTMENT_AND_CHILDREN|SELF|CUSTOM";
    /** 文档状态：草稿仅维护者可见，已发布对可见范围开放，归档下架但保留历史。 */
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

    /**
     * 列表：维护者看到全部文档（含草稿与归档），普通用户只看到可见范围内的已发布文档；
     * 关键字去空白、空串转 null 表示不过滤，分页参数夹在 1~100。可见性条件整体下推 SQL，避免先查后筛。
     */
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

    /** 详情：先过可见性（无权限与不存在同样 404）；版本历史对所有可读者开放，ACL 只给维护者看。 */
    public Map<String, Object> detail(Long id) {
        loadReadable(id);
        Map<String, Object> doc = mapper.findDetail(id);
        doc.put("versions", mapper.findVersions(id));
        if (canManage()) {
            doc.put("acl", mapper.findAcl(id));
        }
        return doc;
    }

    /** 下载：校验可读后只为当前文件签一个短时预签名 URL，服务端不代理字节流。 */
    public Map<String, Object> download(Long id) {
        Map<String, Object> row = loadReadable(id);
        return files.presignRead(Long.valueOf(row.get("fileId").toString()));
    }

    /** 检索服务使用的已发布内容，忽略维护权限，只按可见范围放行；维护者也只能拿到已发布版本。 */
    public Map<String, Object> publishedContent(Long id) {
        Map<String, Object> row = findPublished(id);
        return files.presignRead(Long.valueOf(row.get("fileId").toString()));
    }

    /** 检索服务内部读取已发布原文，避免预签名 URL 的 host 在容器内不可达。 */
    public Map<String, Object> publishedRaw(Long id) {
        Map<String, Object> row = findPublished(id);
        return files.raw(Long.valueOf(row.get("fileId").toString()));
    }

    /** 当前身份可见的已发布文档，供检索前过滤：只回 id 等轻量列，不取正文。 */
    public List<Map<String, Object>> visibleDocumentIds() {
        Visibility visible = visibility();
        return mapper.visibleDocumentIds(visible.userId(), visible.userIdKey(), visible.departmentId(),
                visible.departmentIds(), visible.roles());
    }

    /** 指定时间之后变化的文档元数据（含归档与删除），供增量重建索引；不做数据范围过滤，仅索引进程内部使用。 */
    public List<Map<String, Object>> changedSince(java.time.Instant since) {
        return mapper.changedSince(java.sql.Timestamp.from(since));
    }

    /** 上传预签名：维护者才能申请，前端拿 URL 直传 MinIO，服务端只做文件元数据登记。 */
    public Map<String, Object> presignUpload(KnowledgeUploadRequest request) {
        requireManage();
        return files.presignUpload(request.fileName().trim(), request.contentType(), request.size());
    }

    // ==================== 写入 ====================

    /**
     * 新建文档：校验有效期、CUSTOM 必须有合法 ACL、文件已上传且内容与声明一致；归属部门缺省取当前用户部门。
     * 单号未填则由 KB 序列生成（不回滚），insert 不回填主键故按单号反查 id，再写首版本与 ACL，同一事务。
     */
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

    /**
     * 修改元数据：归档态直接拒；部门缺省沿用原值（Object 需先做 Number 归一化）；ACL 全量替换而非增量合并，
     * 前端必须提交完整授权列表。本方法不改文件与版本。
     */
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

    /**
     * 新增版本：版本号 = 当前版本 + 1，同时把主表 fileId 指向新文件（下载默认取最新版）；
     * 历史版本行会冗余文件名快照，文件后续改名不影响旧版本展示。返回新版本号。
     */
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

    /**
     * 状态流转：只允许 DRAFT→PUBLISHED、PUBLISHED→ARCHIVED、ARCHIVED→PUBLISHED 三条边；
     * 目标状态与当前一致视为幂等成功直接返回。发布前还要校验未过有效期且文件仍在。
     */
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

    /** 删除：仅草稿与归档可删，文档软删（保留版本与审计），ACL 物理删（软删后已无意义）。 */
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

    /** 可读性校验：把“维护者看全部、其他人按可见范围”下推成一条 SQL；查不到统一 404，不泄露存在性。 */
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

    /**
     * 管理视角取文档（不过可见范围，调用前已 requireManage），并把 fileId / ownerDepartmentId /
     * currentVersion 归一化成具体类型——MyBatis 返回的可能是 BigDecimal 或 String，后面要参与运算和比较。
     */
    private Map<String, Object> requireDocument(Long id) {
        Map<String, Object> row = mapper.findManagedDocument(id);
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "知识库文档不存在");
        row.put("fileId", Long.valueOf(row.get("fileId").toString()));
        row.put("ownerDepartmentId", Long.valueOf(row.get("ownerDepartmentId").toString()));
        row.put("currentVersion", Integer.valueOf(row.get("currentVersion").toString()));
        return row;
    }

    /** 可见范围所需的身份参数：departmentIds 与 roles 交给 XML 的 foreach 绑定；部门子树只查一次，避免逐行展开。 */
    private Visibility visibility() {
        CurrentUser user = SecurityUtils.currentUser();
        return new Visibility(user.id(), user.id().toString(), user.departmentId(),
                dataScope.departmentSubtree(user.departmentId()), new ArrayList<>(user.roles()));
    }

    /** 有效期校验：两端都填时起始不得晚于截止；允许只填一端（长期有效/立即生效）。 */
    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "生效起始日期不能晚于截止日期");
        }
    }

    /**
     * CUSTOM 的 ACL 校验：至少一条；USER 类型的 subjectKey 必须能解析成数字且用户存在，
     * 其余按角色名校验存在性——脏授权会让文档对谁都不可见，所以在写入前就拒掉。
     */
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

    /** ACL 采用“先清空再全量插入”的替换语义；非 CUSTOM 只清不插，保证切换可见范围后不留旧授权。 */
    private void replaceAcl(Long documentId, String visibility, List<KnowledgeAclEntry> acl, Long user) {
        mapper.deleteAcl(documentId);
        if (!"CUSTOM".equals(visibility) || acl == null) return;
        for (KnowledgeAclEntry entry : acl) {
            mapper.insertAcl(documentId, entry.subjectType(), entry.subjectKey().trim(), user);
        }
    }

    /** 写版本行时连同文件名一起快照，避免文件被改名或替换后，历史版本列表显示成新名字。 */
    private void insertVersion(Long documentId, int version, Long fileId, String note, Long user) {
        String name = mapper.findFileName(fileId);
        mapper.insertVersion(documentId, version, fileId, name, blank(note), user);
    }

    private String generateCode() {
        return numbers.next("KB");
    }

    /** 文件必须已在 fs_file_metadata 登记（上传完成），否则知识库会指向一个取不到的对象。 */
    private void requireFile(Long fileId) {
        if (mapper.countFile(fileId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件不存在，请先完成上传");
        }
    }

    /** 部门存在性校验；null 也按不存在处理，避免出现没有归属部门的“孤儿文档”。 */
    private void requireDepartment(Long departmentId) {
        if (departmentId == null || mapper.countDepartment(departmentId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "归属部门不存在");
        }
    }

    private void requireManage() {
        if (!canManage()) throw new BusinessException(HttpStatus.FORBIDDEN, "无权维护知识库");
    }

    /** 维护权限判定单独抽成布尔函数：读路径要“是否维护者”决定可见面，写路径要它直接 403。 */
    private boolean canManage() {
        CurrentUser user = SecurityUtils.currentUser();
        return user.roles().contains("ADMIN") || user.permissions().contains("kb:manage");
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }

    /**
     * 日期归一化：不同驱动/不同路径可能给 LocalDate、java.sql.Date、java.util.Date 或字符串，
     * 逐个转换后再比较；null 原样返回，交由调用方判断“没设有效期”。
     */
    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (value == null) return null;
        return LocalDate.parse(value.toString());
    }

    /** 可见范围参数打包：userId 用于“仅本人”，userIdKey 供 SQL 与字符串列比较，departmentIds 为部门子树。 */
    private record Visibility(Long userId, String userIdKey, Long departmentId,
                              List<Long> departmentIds, List<String> roles) {}

    // ==================== 请求模型 ====================

    

    

    

    

    

    
}
