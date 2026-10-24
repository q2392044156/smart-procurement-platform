package com.procuremind.platform.requisition;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.DataScopeService;
import com.procuremind.platform.system.SecurityUtils;
import com.procuremind.platform.workflow.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class RequisitionService {
    private final JdbcTemplate jdbc; private final WorkflowService workflow; private final DataScopeService dataScope;
    public RequisitionService(JdbcTemplate jdbc,WorkflowService workflow,DataScopeService dataScope){this.jdbc=jdbc;this.workflow=workflow;this.dataScope=dataScope;}

    public List<Map<String,Object>> list(){
        DataScopeService.SqlFilter filter=dataScope.requisitionFilter("r");String sql="select cast(r.id as char) id,r.requisition_no requisitionNo,cast(r.applicant_id as char) applicantId,d.name department,u.display_name applicant,r.required_date requiredDate,r.purpose,r.budget_amount budgetAmount,r.status,r.created_at createdAt from pr_requisition r join sys_department d on d.id=r.department_id join sys_user u on u.id=r.applicant_id where r.deleted=0 and "+filter.clause();
        return jdbc.queryForList(sql+" order by r.id desc",filter.args());
    }
    public Map<String,Object> detail(Long id){dataScope.requireRequisition(id);Map<String,Object> data=jdbc.queryForMap("select cast(r.id as char) id,r.requisition_no requisitionNo,cast(r.department_id as char) departmentId,cast(r.applicant_id as char) applicantId,r.required_date requiredDate,r.purpose,r.budget_amount budgetAmount,r.status,r.process_instance_id processInstanceId,r.rejection_reason rejectionReason from pr_requisition r where r.id=? and r.deleted=0",id);data.put("items",jdbc.queryForList("select cast(i.id as char) id,cast(i.material_id as char) materialId,m.code materialCode,m.name materialName,i.quantity,i.estimated_unit_price estimatedUnitPrice,i.remark from pr_requisition_item i join md_material m on m.id=i.material_id where i.requisition_id=? and i.deleted=0",id));return data;}
    @Transactional public Long create(Request r){CurrentUser u=SecurityUtils.currentUser();validate(r);String no="PR"+System.currentTimeMillis();BigDecimal amount=total(r.items());jdbc.update("insert into pr_requisition(requisition_no,department_id,applicant_id,required_date,purpose,budget_amount,created_by,updated_by) values(?,?,?,?,?,?,?,?)",no,u.departmentId(),u.id(),r.requiredDate(),r.purpose(),amount,u.id(),u.id());Long id=jdbc.queryForObject("select id from pr_requisition where requisition_no=?",Long.class,no);insertItems(id,r.items(),u.id());return id;}
    @Transactional public void update(Long id,Request r){Long owner=jdbc.queryForObject("select applicant_id from pr_requisition where id=?",Long.class,id);String status=jdbc.queryForObject("select status from pr_requisition where id=?",String.class,id);if(!owner.equals(SecurityUtils.currentUser().id()))throw new BusinessException(HttpStatus.FORBIDDEN,"只能编辑自己的申请");if(!List.of("DRAFT","REJECTED").contains(status))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能编辑");validate(r);jdbc.update("update pr_requisition set required_date=?,purpose=?,budget_amount=?,status='DRAFT',rejection_reason=null,updated_by=? where id=?",r.requiredDate(),r.purpose(),total(r.items()),owner,id);jdbc.update("delete from pr_requisition_item where requisition_id=?",id);insertItems(id,r.items(),owner);}
    @Transactional public void submit(Long id){Map<String,Object> row=jdbc.queryForMap("select applicant_id,budget_amount,status from pr_requisition where id=? and deleted=0 for update",id);if(!row.get("applicant_id").toString().equals(SecurityUtils.currentUser().id().toString()))throw new BusinessException(HttpStatus.FORBIDDEN,"只能提交自己的申请");if(!List.of("DRAFT","REJECTED").contains(row.get("status").toString()))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能提交");String processId=workflow.startRequisition(id,(BigDecimal)row.get("budget_amount"),SecurityUtils.currentUser());String nextStatus=workflow.isFinished(processId)?"APPROVED":"PENDING_APPROVAL";jdbc.update("update pr_requisition set status=?,process_instance_id=?,updated_by=? where id=?",nextStatus,processId,SecurityUtils.currentUser().id(),id);}
    private void insertItems(Long id,List<Item> items,Long user){for(Item i:items)jdbc.update("insert into pr_requisition_item(requisition_id,material_id,quantity,estimated_unit_price,remark,created_by,updated_by) values(?,?,?,?,?,?,?)",id,i.materialId(),i.quantity(),i.estimatedUnitPrice(),i.remark(),user,user);}
    private void validate(Request r){if(r.requiredDate().isBefore(LocalDate.now()))throw new BusinessException(HttpStatus.BAD_REQUEST,"需求日期不能早于今天");if(r.items()==null||r.items().isEmpty())throw new BusinessException(HttpStatus.BAD_REQUEST,"至少添加一条采购明细");Set<Long> materialIds=new HashSet<>();for(Item i:r.items()){if(i.quantity()==null||i.quantity().signum()<=0||i.estimatedUnitPrice()==null||i.estimatedUnitPrice().signum()<0)throw new BusinessException(HttpStatus.BAD_REQUEST,"数量必须大于0且价格不能为负数");if(!materialIds.add(i.materialId()))throw new BusinessException(HttpStatus.BAD_REQUEST,"同一物资不能重复添加");if(jdbc.queryForObject("select count(*) from md_material where id=? and enabled=1 and deleted=0",Long.class,i.materialId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"采购物资不存在或已停用");}}
    private BigDecimal total(List<Item> items){return items.stream().map(i->i.quantity().multiply(i.estimatedUnitPrice())).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public record Request(@NotNull @FutureOrPresent LocalDate requiredDate,@NotBlank @Size(max=500) String purpose,@NotEmpty @Size(max=100) List<@Valid Item> items){}
    public record Item(@NotNull Long materialId,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity,@NotNull @DecimalMin("0") BigDecimal estimatedUnitPrice,@Size(max=500) String remark){}
}
