package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.SupplierMapper;
import com.procuremind.platform.utils.SecurityUtils;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.procuremind.platform.pojo.request.SupplierContactRequest;
import com.procuremind.platform.pojo.request.SupplierEvaluationRequest;
import com.procuremind.platform.pojo.request.SupplierQualificationRequest;
import com.procuremind.platform.pojo.request.SupplierRequest;

/**
 * 供应商档案：基本信息、联系人、资质与绩效评价，共用一份供应商主档。
 *
 * <p>状态机是主线：新建不能直接落 QUALIFIED，必须先录有效资质；把最后一项有效资质删掉或改坏也会被拦下。
 * 联系人有且只有一个主联系人，供应商行上的 contactName / contactPhone / email 是它的冗余快照，
 * 联系人每次变更后回写，列表页因此不必 join 子表。</p>
 *
 * <p>本类不处理采购订单、付款与准入审批；报价与合同在各自的服务里。依赖：SupplierMapper（主档与三张子表）、
 * SecurityUtils（当前操作人，落审计列）。</p>
 */
@Service
public class SupplierService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final SupplierMapper mapper;
    public SupplierService(SupplierMapper mapper) { this.mapper = mapper; }

    /** 列表：关键字模糊匹配，空白按未传处理。 */
    public List<Map<String,Object>> suppliers(String keyword) {
        return mapper.list(blank(keyword));
    }

    public Map<String,Object> page(String keyword,String status,int current,int size) {
        int page=Math.max(current,1); int pageSize=Math.min(Math.max(size,1),100);
        String key=blank(keyword);
        String state=status==null||status.isBlank()?null:status.trim().toUpperCase();
        long total=mapper.countPage(key,state);
        List<Map<String,Object>> records=mapper.page(key,state,pageSize,(page-1)*pageSize);
        Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("records",records);result.put("total",(int)total);result.put("current",page);result.put("size",pageSize);result.put("pages",(int)((total+pageSize-1)/pageSize));
        return result;
    }

    /** 详情：主档 + 联系人 + 资质 + 评价一次带出，先确认供应商存在。 */
    public Map<String,Object> supplier(Long id) {
        Map<String,Object> data=supplierRow(id);
        data.put("contacts",contacts(id)); data.put("qualifications",qualifications(id)); data.put("evaluations",evaluations(id));
        return data;
    }

    /**
     * 建供应商：禁止直接建成 QUALIFIED（必须先录资质再变更状态）；信用代码查重；请求里带了联系人字段
     * 就顺手建一条主联系人。
     */
    @Transactional public Long createSupplier(SupplierRequest r) {
        if("QUALIFIED".equals(r.status())) throw conflict("新供应商需先录入有效资质，再变更为合格状态");
        validateCreditCode(null,r.unifiedCreditCode()); Long user=SecurityUtils.currentUser().id();
        mapper.insertSupplier(r.code(),r.name(),blank(r.unifiedCreditCode()),r.level(),r.status(),blank(r.contactName()),blank(r.contactPhone()),blank(r.email()),blank(r.address()),user);
        Long id=mapper.findIdByCode(r.code());
        if(hasContact(r)) insertContact(id,new SupplierContactRequest(contactName(r.contactName()),"主要联系人",r.contactPhone(),r.email(),true),user);
        return id;
    }

    /**
     * 改供应商：编码不可改；转为 QUALIFIED 前要求已有有效资质；主联系人快照随之更新。
     */
    @Transactional public void updateSupplier(Long id,SupplierRequest r) {
        Map<String,Object> old=supplierRow(id);
        if(!old.get("code").toString().equals(r.code())) throw conflict("供应商编码创建后不允许修改");
        validateCreditCode(id,r.unifiedCreditCode());
        if("QUALIFIED".equals(r.status())&&!"QUALIFIED".equals(old.get("status"))) requireValidQualification(id);
        mapper.updateSupplier(id,r.name(),blank(r.unifiedCreditCode()),r.level(),r.status(),blank(r.address()),SecurityUtils.currentUser().id());
        if(hasContact(r)) upsertPrimaryContact(id,r); syncPrimaryContact(id);
    }

    /** 单独改状态也要过同一道资质闸门，防止绕过 update 直接置为合格。 */
    public void setSupplierStatus(Long id,String status) {
        requireSupplier(id); if("QUALIFIED".equals(status)) requireValidQualification(id);
        mapper.updateStatus(id,status,SecurityUtils.currentUser().id());
    }

    /** 删供应商：已有采购订单时只能暂停或拉黑；软删级联联系人、资质与评价。 */
    @Transactional public void deleteSupplier(Long id) {
        requireSupplier(id);
        if(mapper.countOrdersBySupplier(id)>0) throw conflict("供应商已产生采购订单，只能暂停或加入黑名单");
        Long user=SecurityUtils.currentUser().id();
        mapper.softDeleteContacts(id,user);
        mapper.softDeleteQualifications(id,user);
        mapper.softDeleteEvaluations(id,user);
        mapper.softDeleteSupplier(id,user);
    }

    public List<Map<String,Object>> contacts(Long supplierId) {
        requireSupplier(supplierId);
        return mapper.findContacts(supplierId);
    }

    /** 建联系人：第一个自动成为主联系人；设主联系人前先清旧的，建完回写供应商快照。 */
    @Transactional public Long createContact(Long supplierId,SupplierContactRequest r) {
        requireSupplier(supplierId); Long user=SecurityUtils.currentUser().id();
        boolean primary=Boolean.TRUE.equals(r.primaryContact())||mapper.countContacts(supplierId)==0;
        if(primary) clearPrimary(supplierId,user);
        Long id=insertContact(supplierId,new SupplierContactRequest(r.name(),r.position(),r.phone(),r.email(),primary),user); syncPrimaryContact(supplierId); return id;
    }

    /** 改联系人：设为主联系人时先清旧主；改完若一个主联系人都不剩，由 ensurePrimary 补一个。 */
    @Transactional public void updateContact(Long supplierId,Long contactId,SupplierContactRequest r) {
        requireContact(supplierId,contactId); Long user=SecurityUtils.currentUser().id();
        if(Boolean.TRUE.equals(r.primaryContact())) clearPrimary(supplierId,user);
        mapper.updateContact(contactId,supplierId,r.name(),blank(r.position()),blank(r.phone()),blank(r.email()),Boolean.TRUE.equals(r.primaryContact()),user);
        ensurePrimary(supplierId,user); syncPrimaryContact(supplierId);
    }

    /** 删联系人：软删后修复主联系人并回写快照，维持「有联系人则恰有一个主联系人」。 */
    @Transactional public void deleteContact(Long supplierId,Long contactId) {
        requireContact(supplierId,contactId); Long user=SecurityUtils.currentUser().id();
        mapper.softDeleteContact(contactId,supplierId,user);
        ensurePrimary(supplierId,user); syncPrimaryContact(supplierId);
    }

    public List<Map<String,Object>> qualifications(Long supplierId) {
        requireSupplier(supplierId);
        return mapper.findQualifications(supplierId);
    }

    /**
     * 录资质：状态由失效日期算出，不接受前端传；同一供应商下资质名称唯一；附件只能关联本人上传的。
     */
    @Transactional public Long createQualification(Long supplierId,SupplierQualificationRequest r) {
        requireSupplier(supplierId); validateQualification(null,supplierId,r); Long user=SecurityUtils.currentUser().id();
        mapper.insertQualification(supplierId,r.name(),r.fileId(),r.validFrom(),r.validUntil(),qualificationStatus(r.validUntil()),user);
        return mapper.findMaxQualificationId(supplierId,r.name());
    }

    /** 改资质：改完若供应商仍是合格，复查至少还有一项有效资质。 */
    @Transactional public void updateQualification(Long supplierId,Long qualificationId,SupplierQualificationRequest r) {
        requireQualification(supplierId,qualificationId); validateQualification(qualificationId,supplierId,r);
        mapper.updateQualification(qualificationId,supplierId,r.name(),r.fileId(),r.validFrom(),r.validUntil(),qualificationStatus(r.validUntil()),SecurityUtils.currentUser().id());
        if("QUALIFIED".equals(supplierRow(supplierId).get("status"))) requireValidQualification(supplierId);
    }

    /** 删资质：合格供应商上同样复查，避免删掉最后一项有效资质。 */
    @Transactional public void deleteQualification(Long supplierId,Long qualificationId) {
        requireQualification(supplierId,qualificationId);
        mapper.softDeleteQualification(qualificationId,supplierId,SecurityUtils.currentUser().id());
        if("QUALIFIED".equals(supplierRow(supplierId).get("status"))) requireValidQualification(supplierId);
    }

    public List<Map<String,Object>> evaluations(Long supplierId) {
        requireSupplier(supplierId);
        return mapper.findEvaluations(supplierId);
    }

    /**
     * 录评价：同一供应商同一期间只能一条；总分是三项等权平均（两位小数、四舍五入），写完立刻重算综合分。
     */
    @Transactional public Long createEvaluation(Long supplierId,SupplierEvaluationRequest r) {
        requireSupplier(supplierId); uniquePeriod(null,supplierId,r.period()); Long user=SecurityUtils.currentUser().id();
        mapper.insertEvaluation(supplierId,r.period(),r.qualityScore(),r.deliveryScore(),r.serviceScore(),total(r),blank(r.comment()),user);
        recalculateScore(supplierId,user);
        return mapper.findMaxEvaluationId(supplierId,r.period());
    }

    /** 改评价：期间唯一性排除自己，改完同样重算综合分。 */
    @Transactional public void updateEvaluation(Long supplierId,Long evaluationId,SupplierEvaluationRequest r) {
        requireEvaluation(supplierId,evaluationId); uniquePeriod(evaluationId,supplierId,r.period()); Long user=SecurityUtils.currentUser().id();
        mapper.updateEvaluation(evaluationId,supplierId,r.period(),r.qualityScore(),r.deliveryScore(),r.serviceScore(),total(r),blank(r.comment()),user); recalculateScore(supplierId,user);
    }

    /** 删评价：软删后重算，避免供应商分数停在旧值。 */
    @Transactional public void deleteEvaluation(Long supplierId,Long evaluationId) {
        requireEvaluation(supplierId,evaluationId); Long user=SecurityUtils.currentUser().id();
        mapper.softDeleteEvaluation(evaluationId,supplierId,user); recalculateScore(supplierId,user);
    }

    private Map<String,Object> supplierRow(Long id) {
        Map<String,Object> row=mapper.findById(id);
        if(row==null) throw notFound("供应商不存在"); return row;
    }
    private void validateCreditCode(Long id,String value) {
        if(value==null||value.isBlank()) return;
        long n=id==null?mapper.countCreditCode(value.trim()):mapper.countCreditCodeExcluding(value.trim(),id);
        if(n>0) throw conflict("统一社会信用代码已存在");
    }
    private void validateQualification(Long id,Long supplierId,SupplierQualificationRequest r) {
        if(r.validFrom()!=null&&r.validUntil()!=null&&r.validFrom().isAfter(r.validUntil())) throw new BusinessException(HttpStatus.BAD_REQUEST,"资质生效日期不能晚于失效日期");
        long n=id==null?mapper.countQualificationName(supplierId,r.name()):mapper.countQualificationNameExcluding(supplierId,r.name(),id);
        if(n>0) throw conflict("同名资质记录已存在"); if(r.fileId()!=null) validateFile(r.fileId());
    }
    /** 只允许挂本人上传的附件：文件 id 可猜，光靠上传接口的鉴权不够。 */
    private void validateFile(Long fileId) {
        Map<String,Object> row=mapper.findFileUploader(fileId);
        if(row==null) throw new BusinessException(HttpStatus.BAD_REQUEST,"附件文件不存在");
        Long uploader=((Number)row.get("uploader_id")).longValue(); var user=SecurityUtils.currentUser();
        if(!uploader.equals(user.id())&&!user.roles().contains("ADMIN")) throw new BusinessException(HttpStatus.FORBIDDEN,"只能关联本人上传的资质附件");
    }
    /** 至少要有一项未撤销且未过期的资质；调用点覆盖「设合格」与「删最后一项」。 */
    private void requireValidQualification(Long supplierId) {
        if(mapper.countValidQualification(supplierId)==0)
            throw conflict("供应商缺少当前有效资质，不能设为合格或删除最后一项有效资质");
    }
    /** 用业务时区的今天判档，不吃服务器时区；30 天内到期算 EXPIRING。 */
    private String qualificationStatus(LocalDate until) {
        if(until==null)return "VALID"; LocalDate today=LocalDate.now(BUSINESS_ZONE);
        return until.isBefore(today)?"EXPIRED":!until.isAfter(today.plusDays(30))?"EXPIRING":"VALID";
    }
    private void uniquePeriod(Long id,Long supplierId,String period) {
        long n=id==null?mapper.countEvaluationPeriod(supplierId,period):mapper.countEvaluationPeriodExcluding(supplierId,period,id);
        if(n>0) throw conflict("该评价期间已存在绩效记录");
    }
    private BigDecimal total(SupplierEvaluationRequest r){return r.qualityScore().add(r.deliveryScore()).add(r.serviceScore()).divide(BigDecimal.valueOf(3),2,RoundingMode.HALF_UP);}
    private void recalculateScore(Long supplierId,Long user){mapper.recalculateScore(supplierId,user);}
    private Long insertContact(Long supplierId,SupplierContactRequest r,Long user){mapper.insertContact(supplierId,r.name(),blank(r.position()),blank(r.phone()),blank(r.email()),Boolean.TRUE.equals(r.primaryContact()),user);return mapper.findMaxContactId(supplierId);}
    private void upsertPrimaryContact(Long supplierId,SupplierRequest r){List<Long> ids=mapper.findFirstContactId(supplierId);Long user=SecurityUtils.currentUser().id();if(ids.isEmpty())insertContact(supplierId,new SupplierContactRequest(contactName(r.contactName()),"主要联系人",r.contactPhone(),r.email(),true),user);else mapper.updatePrimaryContactSnapshot(ids.getFirst(),contactName(r.contactName()),blank(r.contactPhone()),blank(r.email()),user);}
    private void clearPrimary(Long supplierId,Long user){mapper.clearPrimary(supplierId,user);}
    /** 有联系人却没有主联系人时，把最早的一条提为主联系人，恢复不变式。 */
    private void ensurePrimary(Long supplierId,Long user){if(mapper.countContacts(supplierId)>0&&mapper.countPrimaryContacts(supplierId)==0)mapper.promoteFirstContactToPrimary(user,supplierId);}
    /** 把主联系人冗余回供应商主档；没有主联系人时清空快照，避免展示过期联系人。 */
    private void syncPrimaryContact(Long supplierId){Map<String,Object> row=mapper.findPrimaryContactSnapshot(supplierId);Long user=SecurityUtils.currentUser().id();if(row==null)mapper.clearSupplierContactSnapshot(user,supplierId);else mapper.updateSupplierContactSnapshot(supplierId,(String)row.get("name"),(String)row.get("phone"),(String)row.get("email"),user);}
    private boolean hasContact(SupplierRequest r){return text(r.contactName())||text(r.contactPhone())||text(r.email());}
    private boolean text(String v){return v!=null&&!v.isBlank();} private String contactName(String v){return text(v)?v.trim():"未命名联系人";} private String blank(String v){return text(v)?v.trim():null;}
    private void requireSupplier(Long id){supplierRow(id);}
    private void requireContact(Long supplierId,Long id){requireSupplier(supplierId);if(mapper.countContact(id,supplierId)==0)throw notFound("联系人不存在");}
    private void requireQualification(Long supplierId,Long id){requireSupplier(supplierId);if(mapper.countQualification(id,supplierId)==0)throw notFound("资质记录不存在");}
    private void requireEvaluation(Long supplierId,Long id){requireSupplier(supplierId);if(mapper.countEvaluation(id,supplierId)==0)throw notFound("绩效记录不存在");}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);} private BusinessException notFound(String m){return new BusinessException(HttpStatus.NOT_FOUND,m);}

    
    
    
    
    
}
