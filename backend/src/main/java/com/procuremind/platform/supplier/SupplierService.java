package com.procuremind.platform.supplier;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class SupplierService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    public SupplierService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> suppliers(String keyword) {
        String like="%"+(keyword==null?"":keyword.trim())+"%";
        return jdbc.queryForList("""
            select cast(s.id as char) id,s.code,s.name,s.unified_credit_code unifiedCreditCode,s.level,s.status,
              s.contact_name contactName,s.contact_phone contactPhone,s.email,s.address,s.score,
              (select count(*) from sp_supplier_contact c where c.supplier_id=s.id and c.deleted=0) contactCount,
              (select count(*) from sp_supplier_qualification q where q.supplier_id=s.id and q.deleted=0) qualificationCount,
              (select count(*) from sp_supplier_evaluation e where e.supplier_id=s.id and e.deleted=0) evaluationCount,
              (select count(*) from sp_supplier_qualification q where q.supplier_id=s.id and q.deleted=0 and q.status<>'REVOKED' and q.valid_until is not null and q.valid_until<current_date) expiredQualificationCount,
              (select count(*) from po_purchase_order o where o.supplier_id=s.id and o.deleted=0) orderCount
            from sp_supplier s where s.deleted=0 and (s.code like ? or s.name like ?)
            order by field(s.status,'QUALIFIED','PENDING','SUSPENDED','BLACKLISTED'),s.score desc,s.id
            """,like,like);
    }

    public Map<String,Object> supplier(Long id) {
        Map<String,Object> data=supplierRow(id);
        data.put("contacts",contacts(id)); data.put("qualifications",qualifications(id)); data.put("evaluations",evaluations(id));
        return data;
    }

    @Transactional public Long createSupplier(SupplierRequest r) {
        if("QUALIFIED".equals(r.status())) throw conflict("新供应商需先录入有效资质，再变更为合格状态");
        validateCreditCode(null,r.unifiedCreditCode()); Long user=SecurityUtils.currentUser().id();
        jdbc.update("insert into sp_supplier(code,name,unified_credit_code,level,status,contact_name,contact_phone,email,address,created_by,updated_by) values(?,?,?,?,?,?,?,?,?,?,?)",
            r.code(),r.name(),blank(r.unifiedCreditCode()),r.level(),r.status(),blank(r.contactName()),blank(r.contactPhone()),blank(r.email()),blank(r.address()),user,user);
        Long id=jdbc.queryForObject("select id from sp_supplier where code=?",Long.class,r.code());
        if(hasContact(r)) insertContact(id,new ContactRequest(contactName(r.contactName()),"主要联系人",r.contactPhone(),r.email(),true),user);
        return id;
    }

    @Transactional public void updateSupplier(Long id,SupplierRequest r) {
        Map<String,Object> old=supplierRow(id);
        if(!old.get("code").toString().equals(r.code())) throw conflict("供应商编码创建后不允许修改");
        validateCreditCode(id,r.unifiedCreditCode());
        if("QUALIFIED".equals(r.status())&&!"QUALIFIED".equals(old.get("status"))) requireValidQualification(id);
        jdbc.update("update sp_supplier set name=?,unified_credit_code=?,level=?,status=?,address=?,updated_by=? where id=? and deleted=0",
            r.name(),blank(r.unifiedCreditCode()),r.level(),r.status(),blank(r.address()),SecurityUtils.currentUser().id(),id);
        if(hasContact(r)) upsertPrimaryContact(id,r); syncPrimaryContact(id);
    }

    public void setSupplierStatus(Long id,String status) {
        requireSupplier(id); if("QUALIFIED".equals(status)) requireValidQualification(id);
        jdbc.update("update sp_supplier set status=?,updated_by=? where id=? and deleted=0",status,SecurityUtils.currentUser().id(),id);
    }

    @Transactional public void deleteSupplier(Long id) {
        requireSupplier(id);
        if(count("select count(*) from po_purchase_order where supplier_id=? and deleted=0",id)>0) throw conflict("供应商已产生采购订单，只能暂停或加入黑名单");
        Long user=SecurityUtils.currentUser().id();
        jdbc.update("update sp_supplier_contact set deleted=1,updated_by=? where supplier_id=? and deleted=0",user,id);
        jdbc.update("update sp_supplier_qualification set deleted=1,updated_by=? where supplier_id=? and deleted=0",user,id);
        jdbc.update("update sp_supplier_evaluation set deleted=1,updated_by=? where supplier_id=? and deleted=0",user,id);
        jdbc.update("update sp_supplier set deleted=1,status='SUSPENDED',updated_by=? where id=?",user,id);
    }

    public List<Map<String,Object>> contacts(Long supplierId) {
        requireSupplier(supplierId);
        return jdbc.queryForList("select cast(id as char) id,name,position,phone,email,is_primary primaryContact from sp_supplier_contact where supplier_id=? and deleted=0 order by is_primary desc,id",supplierId);
    }

    @Transactional public Long createContact(Long supplierId,ContactRequest r) {
        requireSupplier(supplierId); Long user=SecurityUtils.currentUser().id();
        boolean primary=Boolean.TRUE.equals(r.primaryContact())||count("select count(*) from sp_supplier_contact where supplier_id=? and deleted=0",supplierId)==0;
        if(primary) clearPrimary(supplierId,user);
        Long id=insertContact(supplierId,new ContactRequest(r.name(),r.position(),r.phone(),r.email(),primary),user); syncPrimaryContact(supplierId); return id;
    }

    @Transactional public void updateContact(Long supplierId,Long contactId,ContactRequest r) {
        requireChild("sp_supplier_contact","联系人",supplierId,contactId); Long user=SecurityUtils.currentUser().id();
        if(Boolean.TRUE.equals(r.primaryContact())) clearPrimary(supplierId,user);
        jdbc.update("update sp_supplier_contact set name=?,position=?,phone=?,email=?,is_primary=?,updated_by=? where id=? and supplier_id=? and deleted=0",
            r.name(),blank(r.position()),blank(r.phone()),blank(r.email()),Boolean.TRUE.equals(r.primaryContact()),user,contactId,supplierId);
        ensurePrimary(supplierId,user); syncPrimaryContact(supplierId);
    }

    @Transactional public void deleteContact(Long supplierId,Long contactId) {
        requireChild("sp_supplier_contact","联系人",supplierId,contactId); Long user=SecurityUtils.currentUser().id();
        jdbc.update("update sp_supplier_contact set deleted=1,is_primary=0,updated_by=? where id=? and supplier_id=?",user,contactId,supplierId);
        ensurePrimary(supplierId,user); syncPrimaryContact(supplierId);
    }

    public List<Map<String,Object>> qualifications(Long supplierId) {
        requireSupplier(supplierId);
        return jdbc.queryForList("""
            select cast(q.id as char) id,q.name,cast(q.file_id as char) fileId,f.original_name fileName,q.valid_from validFrom,q.valid_until validUntil,
              case when q.status='REVOKED' then 'REVOKED' when q.valid_until is not null and q.valid_until<current_date then 'EXPIRED'
                   when q.valid_until is not null and q.valid_until<=date_add(current_date,interval 30 day) then 'EXPIRING' else 'VALID' end status
            from sp_supplier_qualification q left join fs_file_metadata f on f.id=q.file_id and f.deleted=0
            where q.supplier_id=? and q.deleted=0 order by q.valid_until is null,q.valid_until desc,q.id
            """,supplierId);
    }

    @Transactional public Long createQualification(Long supplierId,QualificationRequest r) {
        requireSupplier(supplierId); validateQualification(null,supplierId,r); Long user=SecurityUtils.currentUser().id();
        jdbc.update("insert into sp_supplier_qualification(supplier_id,name,file_id,valid_from,valid_until,status,created_by,updated_by) values(?,?,?,?,?,?,?,?)",
            supplierId,r.name(),r.fileId(),r.validFrom(),r.validUntil(),qualificationStatus(r.validUntil()),user,user);
        return jdbc.queryForObject("select max(id) from sp_supplier_qualification where supplier_id=? and name=? and deleted=0",Long.class,supplierId,r.name());
    }

    @Transactional public void updateQualification(Long supplierId,Long qualificationId,QualificationRequest r) {
        requireChild("sp_supplier_qualification","资质记录",supplierId,qualificationId); validateQualification(qualificationId,supplierId,r);
        jdbc.update("update sp_supplier_qualification set name=?,file_id=?,valid_from=?,valid_until=?,status=?,updated_by=? where id=? and supplier_id=? and deleted=0",
            r.name(),r.fileId(),r.validFrom(),r.validUntil(),qualificationStatus(r.validUntil()),SecurityUtils.currentUser().id(),qualificationId,supplierId);
        if("QUALIFIED".equals(supplierRow(supplierId).get("status"))) requireValidQualification(supplierId);
    }

    @Transactional public void deleteQualification(Long supplierId,Long qualificationId) {
        requireChild("sp_supplier_qualification","资质记录",supplierId,qualificationId);
        jdbc.update("update sp_supplier_qualification set deleted=1,updated_by=? where id=? and supplier_id=?",SecurityUtils.currentUser().id(),qualificationId,supplierId);
        if("QUALIFIED".equals(supplierRow(supplierId).get("status"))) requireValidQualification(supplierId);
    }

    public List<Map<String,Object>> evaluations(Long supplierId) {
        requireSupplier(supplierId);
        return jdbc.queryForList("select cast(id as char) id,period,quality_score qualityScore,delivery_score deliveryScore,service_score serviceScore,total_score totalScore,comment from sp_supplier_evaluation where supplier_id=? and deleted=0 order by period desc,id desc",supplierId);
    }

    @Transactional public Long createEvaluation(Long supplierId,EvaluationRequest r) {
        requireSupplier(supplierId); uniquePeriod(null,supplierId,r.period()); Long user=SecurityUtils.currentUser().id();
        jdbc.update("insert into sp_supplier_evaluation(supplier_id,period,quality_score,delivery_score,service_score,total_score,comment,created_by,updated_by) values(?,?,?,?,?,?,?,?,?)",
            supplierId,r.period(),r.qualityScore(),r.deliveryScore(),r.serviceScore(),total(r),blank(r.comment()),user,user);
        recalculateScore(supplierId,user);
        return jdbc.queryForObject("select max(id) from sp_supplier_evaluation where supplier_id=? and period=? and deleted=0",Long.class,supplierId,r.period());
    }

    @Transactional public void updateEvaluation(Long supplierId,Long evaluationId,EvaluationRequest r) {
        requireChild("sp_supplier_evaluation","绩效记录",supplierId,evaluationId); uniquePeriod(evaluationId,supplierId,r.period()); Long user=SecurityUtils.currentUser().id();
        jdbc.update("update sp_supplier_evaluation set period=?,quality_score=?,delivery_score=?,service_score=?,total_score=?,comment=?,updated_by=? where id=? and supplier_id=? and deleted=0",
            r.period(),r.qualityScore(),r.deliveryScore(),r.serviceScore(),total(r),blank(r.comment()),user,evaluationId,supplierId); recalculateScore(supplierId,user);
    }

    @Transactional public void deleteEvaluation(Long supplierId,Long evaluationId) {
        requireChild("sp_supplier_evaluation","绩效记录",supplierId,evaluationId); Long user=SecurityUtils.currentUser().id();
        jdbc.update("update sp_supplier_evaluation set deleted=1,updated_by=? where id=? and supplier_id=?",user,evaluationId,supplierId); recalculateScore(supplierId,user);
    }

    private Map<String,Object> supplierRow(Long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("select cast(id as char) id,code,name,unified_credit_code unifiedCreditCode,level,status,contact_name contactName,contact_phone contactPhone,email,address,score from sp_supplier where id=? and deleted=0",id);
        if(rows.isEmpty()) throw notFound("供应商不存在"); return rows.getFirst();
    }
    private void validateCreditCode(Long id,String value) {
        if(value==null||value.isBlank()) return;
        long n=id==null?count("select count(*) from sp_supplier where unified_credit_code=? and deleted=0",value.trim()):count("select count(*) from sp_supplier where unified_credit_code=? and id<>? and deleted=0",value.trim(),id);
        if(n>0) throw conflict("统一社会信用代码已存在");
    }
    private void validateQualification(Long id,Long supplierId,QualificationRequest r) {
        if(r.validFrom()!=null&&r.validUntil()!=null&&r.validFrom().isAfter(r.validUntil())) throw new BusinessException(HttpStatus.BAD_REQUEST,"资质生效日期不能晚于失效日期");
        long n=id==null?count("select count(*) from sp_supplier_qualification where supplier_id=? and name=? and deleted=0",supplierId,r.name()):count("select count(*) from sp_supplier_qualification where supplier_id=? and name=? and id<>? and deleted=0",supplierId,r.name(),id);
        if(n>0) throw conflict("同名资质记录已存在"); if(r.fileId()!=null) validateFile(r.fileId());
    }
    private void validateFile(Long fileId) {
        List<Map<String,Object>> rows=jdbc.queryForList("select uploader_id from fs_file_metadata where id=? and deleted=0",fileId);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST,"附件文件不存在");
        Long uploader=((Number)rows.getFirst().get("uploader_id")).longValue(); var user=SecurityUtils.currentUser();
        if(!uploader.equals(user.id())&&!user.roles().contains("ADMIN")) throw new BusinessException(HttpStatus.FORBIDDEN,"只能关联本人上传的资质附件");
    }
    private void requireValidQualification(Long supplierId) {
        if(count("select count(*) from sp_supplier_qualification where supplier_id=? and deleted=0 and status<>'REVOKED' and (valid_until is null or valid_until>=current_date)",supplierId)==0)
            throw conflict("供应商缺少当前有效资质，不能设为合格或删除最后一项有效资质");
    }
    private String qualificationStatus(LocalDate until) {
        if(until==null)return "VALID"; LocalDate today=LocalDate.now(BUSINESS_ZONE);
        return until.isBefore(today)?"EXPIRED":!until.isAfter(today.plusDays(30))?"EXPIRING":"VALID";
    }
    private void uniquePeriod(Long id,Long supplierId,String period) {
        long n=id==null?count("select count(*) from sp_supplier_evaluation where supplier_id=? and period=? and deleted=0",supplierId,period):count("select count(*) from sp_supplier_evaluation where supplier_id=? and period=? and id<>? and deleted=0",supplierId,period,id);
        if(n>0) throw conflict("该评价期间已存在绩效记录");
    }
    private BigDecimal total(EvaluationRequest r){return r.qualityScore().add(r.deliveryScore()).add(r.serviceScore()).divide(BigDecimal.valueOf(3),2,RoundingMode.HALF_UP);}
    private void recalculateScore(Long supplierId,Long user){jdbc.update("update sp_supplier set score=(select coalesce(round(avg(total_score),2),0) from sp_supplier_evaluation where supplier_id=? and deleted=0),updated_by=? where id=? and deleted=0",supplierId,user,supplierId);}
    private Long insertContact(Long supplierId,ContactRequest r,Long user){jdbc.update("insert into sp_supplier_contact(supplier_id,name,position,phone,email,is_primary,created_by,updated_by) values(?,?,?,?,?,?,?,?)",supplierId,r.name(),blank(r.position()),blank(r.phone()),blank(r.email()),Boolean.TRUE.equals(r.primaryContact()),user,user);return jdbc.queryForObject("select max(id) from sp_supplier_contact where supplier_id=? and deleted=0",Long.class,supplierId);}
    private void upsertPrimaryContact(Long supplierId,SupplierRequest r){List<Long> ids=jdbc.query("select id from sp_supplier_contact where supplier_id=? and deleted=0 order by is_primary desc,id limit 1",(rs,n)->rs.getLong(1),supplierId);Long user=SecurityUtils.currentUser().id();if(ids.isEmpty())insertContact(supplierId,new ContactRequest(contactName(r.contactName()),"主要联系人",r.contactPhone(),r.email(),true),user);else jdbc.update("update sp_supplier_contact set name=?,phone=?,email=?,is_primary=1,updated_by=? where id=?",contactName(r.contactName()),blank(r.contactPhone()),blank(r.email()),user,ids.getFirst());}
    private void clearPrimary(Long supplierId,Long user){jdbc.update("update sp_supplier_contact set is_primary=0,updated_by=? where supplier_id=? and deleted=0 and is_primary=1",user,supplierId);}
    private void ensurePrimary(Long supplierId,Long user){if(count("select count(*) from sp_supplier_contact where supplier_id=? and deleted=0",supplierId)>0&&count("select count(*) from sp_supplier_contact where supplier_id=? and deleted=0 and is_primary=1",supplierId)==0)jdbc.update("update sp_supplier_contact set is_primary=1,updated_by=? where id=(select id from (select id from sp_supplier_contact where supplier_id=? and deleted=0 order by id limit 1) c)",user,supplierId);}
    private void syncPrimaryContact(Long supplierId){List<Map<String,Object>> rows=jdbc.queryForList("select name,phone,email from sp_supplier_contact where supplier_id=? and deleted=0 order by is_primary desc,id limit 1",supplierId);Long user=SecurityUtils.currentUser().id();if(rows.isEmpty())jdbc.update("update sp_supplier set contact_name=null,contact_phone=null,email=null,updated_by=? where id=?",user,supplierId);else{var c=rows.getFirst();jdbc.update("update sp_supplier set contact_name=?,contact_phone=?,email=?,updated_by=? where id=?",c.get("name"),c.get("phone"),c.get("email"),user,supplierId);}}
    private boolean hasContact(SupplierRequest r){return text(r.contactName())||text(r.contactPhone())||text(r.email());}
    private boolean text(String v){return v!=null&&!v.isBlank();} private String contactName(String v){return text(v)?v.trim():"未命名联系人";} private String blank(String v){return text(v)?v.trim():null;}
    private void requireSupplier(Long id){supplierRow(id);} private void requireChild(String table,String name,Long supplierId,Long id){requireSupplier(supplierId);if(count("select count(*) from "+table+" where id=? and supplier_id=? and deleted=0",id,supplierId)==0)throw notFound(name+"不存在");}
    private long count(String sql,Object...args){Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n;} private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);} private BusinessException notFound(String m){return new BusinessException(HttpStatus.NOT_FOUND,m);}

    public record SupplierRequest(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_-]{1,49}") String code,@NotBlank @Size(max=200) String name,@Size(max=50) String unifiedCreditCode,@NotBlank @Pattern(regexp="A|B|C|D") String level,@NotBlank @Pattern(regexp="PENDING|QUALIFIED|SUSPENDED|BLACKLISTED") String status,@Size(max=100) String contactName,@Size(max=50) String contactPhone,@Email @Size(max=100) String email,@Size(max=300) String address){}
    public record SupplierStatusRequest(@NotBlank @Pattern(regexp="PENDING|QUALIFIED|SUSPENDED|BLACKLISTED") String status){}
    public record ContactRequest(@NotBlank @Size(max=100) String name,@Size(max=100) String position,@Size(max=50) String phone,@Email @Size(max=100) String email,@NotNull Boolean primaryContact){}
    public record QualificationRequest(@NotBlank @Size(max=200) String name,@Positive Long fileId,LocalDate validFrom,LocalDate validUntil){}
    public record EvaluationRequest(@NotBlank @Pattern(regexp="20\\d{2}-Q[1-4]") String period,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal qualityScore,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal deliveryScore,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal serviceScore,@Size(max=500) String comment){}
}
