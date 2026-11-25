package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.service.DataScopeService;
import com.procuremind.platform.utils.SecurityUtils;
import com.procuremind.platform.service.UserDirectory;
import com.procuremind.platform.mapper.WorkflowMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class WorkflowService {
    private final RuntimeService runtime;private final TaskService tasks;private final HistoryService history;private final WorkflowMapper mapper;private final UserDirectory users;private final DataScopeService dataScope;
    public WorkflowService(RuntimeService runtime,TaskService tasks,HistoryService history,WorkflowMapper mapper,UserDirectory users,DataScopeService dataScope){this.runtime=runtime;this.tasks=tasks;this.history=history;this.mapper=mapper;this.users=users;this.dataScope=dataScope;}
    public String startRequisition(Long id,BigDecimal amount,CurrentUser applicant){ProcessInstance p=runtime.startProcessInstanceByKey("requisitionApproval","REQUISITION:"+id,startVariables(amount,applicant));skipSelf(p.getId(),applicant);return p.getId();}
    public String startPurchaseOrder(Long id,BigDecimal amount,CurrentUser applicant){ProcessInstance p=runtime.startProcessInstanceByKey("purchaseOrderApproval","PURCHASE_ORDER:"+id,startVariables(amount,applicant));skipSelf(p.getId(),applicant);return p.getId();}
    private static Map<String,Object> startVariables(BigDecimal amount,CurrentUser applicant){Map<String,Object> variables=new HashMap<>();variables.put("amount",amount==null?BigDecimal.ZERO:amount);variables.put("applicantId",applicant.id().toString());return variables;}
    public boolean isFinished(String processId){return runtime.createProcessInstanceQuery().processInstanceId(processId).count()==0;}
    public void cancelActiveProcesses(String businessKey,String reason){runtime.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).active().list().forEach(p->runtime.deleteProcessInstance(p.getId(),"CANCELLED: "+reason));}
    private void skipSelf(String processId,CurrentUser applicant){for(int i=0;i<8;i++){Task task=tasks.createTaskQuery().processInstanceId(processId).singleResult();if(task==null)return;Set<String> groups=new HashSet<>();tasks.getIdentityLinksForTask(task.getId()).stream().filter(x->"candidate".equals(x.getType())&&x.getGroupId()!=null).forEach(x->groups.add(x.getGroupId()));if(Collections.disjoint(groups,applicant.roles()))return;tasks.addComment(task.getId(),processId,"申请人兼任当前审批角色，系统自动跳过");tasks.complete(task.getId(),Map.of("approved",true));}}
    public List<Map<String,Object>> todo(){CurrentUser u=SecurityUtils.currentUser();if(u.roles().isEmpty())return List.of();return tasks.createTaskQuery().taskCandidateGroupIn(new ArrayList<>(u.roles())).active().orderByTaskCreateTime().desc().list().stream().filter(this::canAccessTask).map(this::taskMap).toList();}
    public long todoCount(){return todo().size();}
    public List<Map<String,Object>> done(){String id=SecurityUtils.currentUser().id().toString();return history.createHistoricTaskInstanceQuery().taskAssignee(id).finished().orderByHistoricTaskInstanceEndTime().desc().listPage(0,100).stream().map(this::taskMap).toList();}
    @Transactional public void approve(String taskId,String comment){Task t=requireCandidate(taskId);tasks.claim(taskId,SecurityUtils.currentUser().id().toString());if(comment!=null&&!comment.isBlank())tasks.addComment(taskId,t.getProcessInstanceId(),comment);String businessKey=runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).singleResult().getBusinessKey();tasks.complete(taskId,Map.of("approved",true));if(runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).count()==0)updateStatus(businessKey,"APPROVED",null);}
    @Transactional public void reject(String taskId,String comment){if(comment==null||comment.isBlank())throw new BusinessException(HttpStatus.BAD_REQUEST,"驳回必须填写意见");Task t=requireCandidate(taskId);tasks.claim(taskId,SecurityUtils.currentUser().id().toString());tasks.addComment(taskId,t.getProcessInstanceId(),comment);String businessKey=runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).singleResult().getBusinessKey();runtime.deleteProcessInstance(t.getProcessInstanceId(),"REJECTED: "+comment);updateStatus(businessKey,"REJECTED",comment);}
    public List<Map<String,Object>> timeline(String businessType,Long businessId){if("REQUISITION".equals(businessType))dataScope.requireRequisition(businessId);else if("PURCHASE_ORDER".equals(businessType))dataScope.requirePurchaseOrder(businessId);else throw new BusinessException(HttpStatus.BAD_REQUEST,"不支持的业务类型");String key=businessType+":"+businessId;var instances=history.createHistoricProcessInstanceQuery().processInstanceBusinessKey(key).orderByProcessInstanceStartTime().asc().list();List<Map<String,Object>> result=new ArrayList<>();for(var p:instances){for(HistoricActivityInstance a:history.createHistoricActivityInstanceQuery().processInstanceId(p.getId()).activityType("userTask").orderByHistoricActivityInstanceStartTime().asc().list()){Map<String,Object> row=new LinkedHashMap<>();row.put("processInstanceId",p.getId());row.put("name",a.getActivityName());row.put("assignee",a.getAssignee());row.put("startTime",a.getStartTime());row.put("endTime",a.getEndTime());row.put("durationInMillis",a.getDurationInMillis());result.add(row);}}return result;}
    private Task requireCandidate(String id){Task t=tasks.createTaskQuery().taskId(id).singleResult();if(t==null)throw new BusinessException(HttpStatus.NOT_FOUND,"审批任务不存在或已处理");CurrentUser u=SecurityUtils.currentUser();boolean allowed=tasks.getIdentityLinksForTask(id).stream().anyMatch(x->u.roles().contains(x.getGroupId()))||u.id().toString().equals(t.getAssignee());if(!allowed)throw new BusinessException(HttpStatus.FORBIDDEN,"该任务不属于当前用户");ProcessInstance process=runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).singleResult();if(process==null)throw new BusinessException(HttpStatus.NOT_FOUND,"审批流程不存在或已结束");dataScope.requireBusinessKey(process.getBusinessKey());String applicant=String.valueOf(runtime.getVariable(t.getProcessInstanceId(),"applicantId"));if(applicant.equals(u.id().toString()))throw new BusinessException(HttpStatus.FORBIDDEN,"申请人不能审批自己的单据");return t;}
    private boolean canAccessTask(Task task){ProcessInstance process=runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();return process!=null&&dataScope.canAccessBusinessKey(process.getBusinessKey());}
    private void updateStatus(String key,String status,String reason){String[] p=key.split(":");if("REQUISITION".equals(p[0]))mapper.updateRequisitionStatus(Long.valueOf(p[1]),status,reason,SecurityUtils.currentUser().id());else mapper.updatePurchaseOrderStatus(Long.valueOf(p[1]),status,SecurityUtils.currentUser().id());}
    private Map<String,Object> taskMap(TaskInfo t){Map<String,Object> row=new LinkedHashMap<>();row.put("id",t.getId());row.put("name",t.getName());row.put("processInstanceId",t.getProcessInstanceId());row.put("createdAt",t.getCreateTime());if(t instanceof Task task){ProcessInstance p=runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();row.put("businessKey",p==null?null:p.getBusinessKey());}return row;}
}
