package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.FileMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.AttachmentRequest;

/**
 * 单据附件的绑定关系：维护“业务单据（采购申请 / 采购订单）↔ 已上传文件”的多对多关联。
 *
 * <p>边界：只写关联表，不复制文件、不读取对象存储内容（内容校验委托 FileContentValidator），
 * 也不物理删除文件；删除单据时只解绑，文件本身仍留在文件域，是否可删由 FileStorageService 判断。
 *
 * <p>依赖：FileMapper（附件与文件元数据查询）、DataScopeService（读范围校验）、FileContentValidator
 * （关联前验真文件类型）。attach/delete 有事务；list 等只读方法无事务。</p>
 */
@Service
public class BusinessAttachmentService {
    private final FileMapper mapper;
    private final DataScopeService dataScope;
    private final FileContentValidator contentValidator;

    public BusinessAttachmentService(FileMapper mapper, DataScopeService dataScope, FileContentValidator contentValidator) {
        this.mapper = mapper;
        this.dataScope = dataScope;
        this.contentValidator = contentValidator;
    }

    /** 列单据附件：先按类型查数据范围（越权在查库前就被拦下），再取未删除的关联记录。 */
    public List<Map<String,Object>> list(String businessType,Long businessId) {
        BusinessType type=parse(businessType); requireRead(type,businessId);
        return mapper.listAttachments(type.name(),businessId);
    }

    /**
     * 绑定附件：写权限 → 文件归属 → 内容验真 → 同单据同文件去重 → 插入关联。
     * 返回 findMaxAttachmentId 反查的主键（insert 不回填主键），依赖同一事务内自增序列的连续性。
     */
    @Transactional public Long attach(String businessType,Long businessId,AttachmentRequest request) {
        BusinessType type=parse(businessType); requireWrite(type,businessId); requireOwnedFile(request.fileId());
        contentValidator.verify(request.fileId());
        if(mapper.countAttachment(type.name(),businessId,request.fileId())>0)
            throw new BusinessException(HttpStatus.CONFLICT,"该文件已经关联当前单据");
        Long user=SecurityUtils.currentUser().id();
        mapper.insertAttachment(type.name(),businessId,request.fileId(),request.category(),blank(request.note()),user);
        return mapper.findMaxAttachmentId(type.name(),businessId);
    }

    /**
     * 解绑附件：只软删关联记录，不碰 MinIO 对象。权限用记录上的 businessType/businessId 反查，
     * 因为这时的入参只有附件 id，不能信任前端另传的业务归属。
     */
    @Transactional public void delete(Long attachmentId) {
        Map<String,Object> row=mapper.findAttachment(attachmentId);
        if(row==null) throw new BusinessException(HttpStatus.NOT_FOUND,"附件关联不存在");
        BusinessType type=parse(row.get("businessType").toString()); Long businessId=((Number)row.get("businessId")).longValue();
        requireWrite(type,businessId);
        mapper.softDeleteAttachment(attachmentId,SecurityUtils.currentUser().id());
    }

    /** 单据软删时批量解绑，保证附件列表不再挂到已删除单据；不校验当前用户的数据范围（由调用方负责）。 */
    public void unlinkBusiness(String businessType,Long businessId) {
        BusinessType type=parse(businessType);
        mapper.unlinkBusiness(type.name(),businessId,SecurityUtils.currentUser().id());
    }

    /** 文件是否还被任一单据引用，供文件删除前拦截（与 FileReferenceChecker 是并列的两道检查）。 */
    public boolean isFileLinked(Long fileId) {
        return mapper.countByFile(fileId)>0;
    }

    /**
     * 判断当前用户能否读该文件所挂的业务单据：逐个关联尝试，任一通过即可读。
     * 这里把 BusinessException 当“不可读”的正常信号吞掉，遍历完仍无命中才返回 false。
     */
    public boolean canReadFile(Long fileId) {
        for(Map<String,Object> row:mapper.listByFile(fileId)) {
            try { requireRead(parse(row.get("businessType").toString()),((Number)row.get("businessId")).longValue()); return true; }
            catch (BusinessException ignored) { }
        }
        return false;
    }

    /** 两类业务的读校验入口：走各自的数据范围服务，抛出的异常即拒绝访问。 */
    private void requireRead(BusinessType type,Long businessId) {
        if(type==BusinessType.REQUISITION)dataScope.requireRequisition(businessId); else dataScope.requirePurchaseOrder(businessId);
    }

    /**
     * 写校验：先过读范围，再要求“本人（或 ADMIN）且单据处于草稿/驳回”。写成计数查询而不是取实体，
     * 是因为这里只需要一个是非结论，同时把状态判断下推到 SQL，避免应用层与库中状态判断不一致。
     */
    private void requireWrite(BusinessType type,Long businessId) {
        requireRead(type,businessId); Long user=SecurityUtils.currentUser().id(); boolean admin=SecurityUtils.currentUser().roles().contains("ADMIN");
        int writable = type==BusinessType.REQUISITION
                ? mapper.countWritableRequisition(businessId,user,admin?1:0)
                : mapper.countWritablePurchaseOrder(businessId,user,admin?1:0);
        if(writable==0) throw new BusinessException(HttpStatus.CONFLICT,"只有单据所有人可在草稿或驳回状态维护附件");
    }

    /** 防“挂别人文件”：上传者本人或 ADMIN 才能建立关联；文件不存在按 400（对调用方是入参错误）。 */
    private void requireOwnedFile(Long fileId) {
        Long uploaderId=mapper.findUploaderId(fileId);
        if(uploaderId==null)throw new BusinessException(HttpStatus.BAD_REQUEST,"附件文件不存在");
        var user=SecurityUtils.currentUser();
        if(!uploaderId.equals(user.id())&&!user.roles().contains("ADMIN"))throw new BusinessException(HttpStatus.FORBIDDEN,"只能关联本人上传的文件");
    }

    /** 业务类型白名单：未知类型一律 400，避免脏字符串被拼进表名/条件。 */
    private BusinessType parse(String value){try{return BusinessType.valueOf(value.toUpperCase());}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,"不支持的附件业务类型");}}
    /** 空串归一化为 null，避免库里出现 “” 与 NULL 两种“空备注”。 */
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    /** 附件可挂载的业务类型；新增枚举值必须同步 requireRead/requireWrite 的分支，否则会走错校验。 */
    private enum BusinessType { REQUISITION,PURCHASE_ORDER }
    
}
