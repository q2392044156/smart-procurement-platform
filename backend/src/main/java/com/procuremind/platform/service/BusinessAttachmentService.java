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

    public List<Map<String,Object>> list(String businessType,Long businessId) {
        BusinessType type=parse(businessType); requireRead(type,businessId);
        return mapper.listAttachments(type.name(),businessId);
    }

    @Transactional public Long attach(String businessType,Long businessId,AttachmentRequest request) {
        BusinessType type=parse(businessType); requireWrite(type,businessId); requireOwnedFile(request.fileId());
        contentValidator.verify(request.fileId());
        if(mapper.countAttachment(type.name(),businessId,request.fileId())>0)
            throw new BusinessException(HttpStatus.CONFLICT,"该文件已经关联当前单据");
        Long user=SecurityUtils.currentUser().id();
        mapper.insertAttachment(type.name(),businessId,request.fileId(),request.category(),blank(request.note()),user);
        return mapper.findMaxAttachmentId(type.name(),businessId);
    }

    @Transactional public void delete(Long attachmentId) {
        Map<String,Object> row=mapper.findAttachment(attachmentId);
        if(row==null) throw new BusinessException(HttpStatus.NOT_FOUND,"附件关联不存在");
        BusinessType type=parse(row.get("businessType").toString()); Long businessId=((Number)row.get("businessId")).longValue();
        requireWrite(type,businessId);
        mapper.softDeleteAttachment(attachmentId,SecurityUtils.currentUser().id());
    }

    public void unlinkBusiness(String businessType,Long businessId) {
        BusinessType type=parse(businessType);
        mapper.unlinkBusiness(type.name(),businessId,SecurityUtils.currentUser().id());
    }

    public boolean isFileLinked(Long fileId) {
        return mapper.countByFile(fileId)>0;
    }

    public boolean canReadFile(Long fileId) {
        for(Map<String,Object> row:mapper.listByFile(fileId)) {
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
        int writable = type==BusinessType.REQUISITION
                ? mapper.countWritableRequisition(businessId,user,admin?1:0)
                : mapper.countWritablePurchaseOrder(businessId,user,admin?1:0);
        if(writable==0) throw new BusinessException(HttpStatus.CONFLICT,"只有单据所有人可在草稿或驳回状态维护附件");
    }

    private void requireOwnedFile(Long fileId) {
        Long uploaderId=mapper.findUploaderId(fileId);
        if(uploaderId==null)throw new BusinessException(HttpStatus.BAD_REQUEST,"附件文件不存在");
        var user=SecurityUtils.currentUser();
        if(!uploaderId.equals(user.id())&&!user.roles().contains("ADMIN"))throw new BusinessException(HttpStatus.FORBIDDEN,"只能关联本人上传的文件");
    }

    private BusinessType parse(String value){try{return BusinessType.valueOf(value.toUpperCase());}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,"不支持的附件业务类型");}}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private enum BusinessType { REQUISITION,PURCHASE_ORDER }
    
}
