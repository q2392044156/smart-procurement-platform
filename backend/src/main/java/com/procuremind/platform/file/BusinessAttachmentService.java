package com.procuremind.platform.file;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.DataScopeService;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class BusinessAttachmentService {
    private final JdbcTemplate jdbc;
    private final DataScopeService dataScope;

    public BusinessAttachmentService(JdbcTemplate jdbc, DataScopeService dataScope) {
        this.jdbc = jdbc;
        this.dataScope = dataScope;
    }

    public List<Map<String,Object>> list(String businessType,Long businessId) {
        BusinessType type=parse(businessType); requireRead(type,businessId);
        return jdbc.queryForList("""
            select cast(a.id as char) id,a.business_type businessType,cast(a.business_id as char) businessId,
                   cast(a.file_id as char) fileId,f.original_name fileName,f.content_type contentType,f.size_bytes sizeBytes,
                   a.category,a.note,a.created_at createdAt
            from fs_business_attachment a join fs_file_metadata f on f.id=a.file_id and f.deleted=0
            where a.business_type=? and a.business_id=? and a.deleted=0 order by a.id desc
            """,type.name(),businessId);
    }

    @Transactional public Long attach(String businessType,Long businessId,AttachmentRequest request) {
        BusinessType type=parse(businessType); requireWrite(type,businessId); requireOwnedFile(request.fileId());
        if(count("select count(*) from fs_business_attachment where business_type=? and business_id=? and file_id=? and deleted=0",type.name(),businessId,request.fileId())>0)
            throw new BusinessException(HttpStatus.CONFLICT,"该文件已经关联当前单据");
        Long user=SecurityUtils.currentUser().id();
        jdbc.update("insert into fs_business_attachment(business_type,business_id,file_id,category,note,created_by,updated_by) values(?,?,?,?,?,?,?)",
                type.name(),businessId,request.fileId(),request.category(),blank(request.note()),user,user);
        return jdbc.queryForObject("select max(id) from fs_business_attachment where business_type=? and business_id=? and deleted=0",Long.class,type.name(),businessId);
    }

    @Transactional public void delete(Long attachmentId) {
        List<Map<String,Object>> rows=jdbc.queryForList("select business_type businessType,business_id businessId from fs_business_attachment where id=? and deleted=0",attachmentId);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"附件关联不存在");
        Map<String,Object> row=rows.getFirst(); BusinessType type=parse(row.get("businessType").toString()); Long businessId=((Number)row.get("businessId")).longValue();
        requireWrite(type,businessId);
        jdbc.update("update fs_business_attachment set deleted=1,updated_by=? where id=?",SecurityUtils.currentUser().id(),attachmentId);
    }

    public void unlinkBusiness(String businessType,Long businessId) {
        BusinessType type=parse(businessType);
        jdbc.update("update fs_business_attachment set deleted=1,updated_by=? where business_type=? and business_id=? and deleted=0",
                SecurityUtils.currentUser().id(),type.name(),businessId);
    }

    public boolean isFileLinked(Long fileId) {
        return count("select count(*) from fs_business_attachment where file_id=? and deleted=0",fileId)>0;
    }

    public boolean canReadFile(Long fileId) {
        for(Map<String,Object> row:jdbc.queryForList("select business_type businessType,business_id businessId from fs_business_attachment where file_id=? and deleted=0",fileId)) {
            try { requireRead(parse(row.get("businessType").toString()),((Number)row.get("businessId")).longValue()); return true; }
            catch (BusinessException ignored) { }
        }
        return false;
    }

    private void requireRead(BusinessType type,Long businessId) {
        if(type==BusinessType.REQUISITION)dataScope.requireRequisition(businessId); else dataScope.requirePurchaseOrder(businessId);
    }

    private void requireWrite(BusinessType type,Long businessId) {
        requireRead(type,businessId); Long user=SecurityUtils.currentUser().id(); boolean admin=SecurityUtils.currentUser().roles().contains("ADMIN");
        String table=type==BusinessType.REQUISITION?"pr_requisition":"po_purchase_order";
        String owner=type==BusinessType.REQUISITION?"applicant_id":"purchaser_id";
        Long count=jdbc.queryForObject("select count(*) from "+table+" where id=? and deleted=0 and status in ('DRAFT','REJECTED') and ("+owner+"=? or ?=1)",Long.class,businessId,user,admin?1:0);
        if(count==null||count==0) throw new BusinessException(HttpStatus.CONFLICT,"只有单据所有人可在草稿或驳回状态维护附件");
    }

    private void requireOwnedFile(Long fileId) {
        List<Long> uploaders=jdbc.queryForList("select uploader_id from fs_file_metadata where id=? and deleted=0",Long.class,fileId);
        if(uploaders.isEmpty())throw new BusinessException(HttpStatus.BAD_REQUEST,"附件文件不存在");
        var user=SecurityUtils.currentUser();
        if(!uploaders.getFirst().equals(user.id())&&!user.roles().contains("ADMIN"))throw new BusinessException(HttpStatus.FORBIDDEN,"只能关联本人上传的文件");
    }

    private BusinessType parse(String value){try{return BusinessType.valueOf(value.toUpperCase());}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,"不支持的附件业务类型");}}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private enum BusinessType { REQUISITION,PURCHASE_ORDER }
    public record AttachmentRequest(@NotNull @Positive Long fileId,@NotBlank @Pattern(regexp="REQUEST|QUOTATION|CONTRACT|TECHNICAL|OTHER") String category,@Size(max=300) String note){}
}
