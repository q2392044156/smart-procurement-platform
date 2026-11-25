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

/**
 * Flowable 审批流程的统一封装：启动申请/订单审批、待办与已办、通过/驳回、审批时间线，并把结果回写到业务单据。
 *
 * <p>约定：processDefinitionKey 为 requisitionApproval / purchaseOrderApproval，businessKey 为
 * “业务类型:主键”（如 REQUISITION:12），回写状态时靠它反解类型与 id。申请人不能审批自己的单据；
 * 若申请人恰好兼任当前节点的候选审批角色，启动后会自动通过该节点并留痕，最多跳 8 层防止流程回环。
 *
 * <p>依赖 Flowable 的 RuntimeService/TaskService/HistoryService（流程引擎）、WorkflowMapper（状态回写）、
 * DataScopeService（数据范围过滤）与 UserDirectory（人员信息）。本类不改库存、不写业务明细表。</p>
 */
@Service
public class WorkflowService {
    private final RuntimeService runtime;private final TaskService tasks;private final HistoryService history;private final WorkflowMapper mapper;private final UserDirectory users;private final DataScopeService dataScope;
    public WorkflowService(RuntimeService runtime,TaskService tasks,HistoryService history,WorkflowMapper mapper,UserDirectory users,DataScopeService dataScope){this.runtime=runtime;this.tasks=tasks;this.history=history;this.mapper=mapper;this.users=users;this.dataScope=dataScope;}
    /** 启动采购申请审批：金额作为流程变量参与分支判断，businessKey 用于后续回写与终止；启动后尝试跳过自审节点。 */
    public String startRequisition(Long id,BigDecimal amount,CurrentUser applicant){ProcessInstance p=runtime.startProcessInstanceByKey("requisitionApproval","REQUISITION:"+id,startVariables(amount,applicant));skipSelf(p.getId(),applicant);return p.getId();}
    /** 启动采购订单审批，规则与申请一致；订单的 businessKey 前缀是 PURCHASE_ORDER。 */
    public String startPurchaseOrder(Long id,BigDecimal amount,CurrentUser applicant){ProcessInstance p=runtime.startProcessInstanceByKey("purchaseOrderApproval","PURCHASE_ORDER:"+id,startVariables(amount,applicant));skipSelf(p.getId(),applicant);return p.getId();}
    /** 流程变量只放审批真正需要的两项：金额（可能为 null，归零）与申请人工号字符串（便于与登录 id 比较）。 */
    private static Map<String,Object> startVariables(BigDecimal amount,CurrentUser applicant){Map<String,Object> variables=new HashMap<>();variables.put("amount",amount==null?BigDecimal.ZERO:amount);variables.put("applicantId",applicant.id().toString());return variables;}
    /** 运行时实例已不存在即视为流程结束（正常结束或已被删除），调用方据此决定终态。 */
    public boolean isFinished(String processId){return runtime.createProcessInstanceQuery().processInstanceId(processId).count()==0;}
    /** 按业务键终止该单据的所有活动流程实例（取消单据时用），删除原因带 CANCELLED 前缀便于从历史里区分。 */
    public void cancelActiveProcesses(String businessKey,String reason){runtime.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).active().list().forEach(p->runtime.deleteProcessInstance(p.getId(),"CANCELLED: "+reason));}
    /**
     * 自动跳过“申请人兼任审批人”的节点：取当前唯一任务，若候选组与申请人角色有交集就写意见并自动通过。
     * 循环上限 8 次是防御性的——流程若因分支回环反复回到同一角色，这里必须能停下来而不是死循环。
     */
    private void skipSelf(String processId,CurrentUser applicant){for(int i=0;i<8;i++){Task task=tasks.createTaskQuery().processInstanceId(processId).singleResult();if(task==null)return;Set<String> groups=new HashSet<>();tasks.getIdentityLinksForTask(task.getId()).stream().filter(x->"candidate".equals(x.getType())&&x.getGroupId()!=null).forEach(x->groups.add(x.getGroupId()));if(Collections.disjoint(groups,applicant.roles()))return;tasks.addComment(task.getId(),processId,"申请人兼任当前审批角色，系统自动跳过");tasks.complete(task.getId(),Map.of("approved",true));}}
    /**
     * 我的待办：按当前用户的角色查候选组任务，再逐条做数据范围过滤（同一角色可见的单据范围可能不同），
     * 按创建时间倒序。角色为空直接返回空列表，省掉一次无意义的流程查询。
     */
    public List<Map<String,Object>> todo(){CurrentUser u=SecurityUtils.currentUser();if(u.roles().isEmpty())return List.of();return tasks.createTaskQuery().taskCandidateGroupIn(new ArrayList<>(u.roles())).active().orderByTaskCreateTime().desc().list().stream().filter(this::canAccessTask).map(this::taskMap).toList();}
    /** 待办数量取的是过滤后的列表大小，不额外走 count 查询，保证与 todo() 展示口径完全一致。 */
    public long todoCount(){return todo().size();}
    /** 我已办：历史任务按办理人查，已完成的取最近 100 条，不再分页（首页/角标用途）。 */
    public List<Map<String,Object>> done(){String id=SecurityUtils.currentUser().id().toString();return history.createHistoricTaskInstanceQuery().taskAssignee(id).finished().orderByHistoricTaskInstanceEndTime().desc().listPage(0,100).stream().map(this::taskMap).toList();}
    /**
     * 通过：先校验候选人身份与数据范围 → 认领任务（避免并发重复处理）→ 记录意见 → 完成任务；
     * 若流程已无活动实例说明这是最后一个节点，此时把业务单据置 APPROVED，否则等后续节点继续。
     */
    @Transactional public void approve(String taskId,String comment){Task t=requireCandidate(taskId);tasks.claim(taskId,SecurityUtils.currentUser().id().toString());if(comment!=null&&!comment.isBlank())tasks.addComment(taskId,t.getProcessInstanceId(),comment);String businessKey=runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).singleResult().getBusinessKey();tasks.complete(taskId,Map.of("approved",true));if(runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).count()==0)updateStatus(businessKey,"APPROVED",null);}
    /**
     * 驳回：意见必填，否则审计无据。与通过不同，驳回直接删除流程实例（而不是完成节点），
     * 因此历史里看到的是 deleteReason=REJECTED:xxx，业务单据同时置 REJECTED 并带上驳回意见。
     */
    @Transactional public void reject(String taskId,String comment){if(comment==null||comment.isBlank())throw new BusinessException(HttpStatus.BAD_REQUEST,"驳回必须填写意见");Task t=requireCandidate(taskId);tasks.claim(taskId,SecurityUtils.currentUser().id().toString());tasks.addComment(taskId,t.getProcessInstanceId(),comment);String businessKey=runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).singleResult().getBusinessKey();runtime.deleteProcessInstance(t.getProcessInstanceId(),"REJECTED: "+comment);updateStatus(businessKey,"REJECTED",comment);}
    /**
     * 审批时间线：按业务键取全部历史流程实例，再逐个取 userTask 节点，拼成带审批人/起止时间的行。
     * 先按业务类型做数据范围校验，未知类型直接 400；同一单据重新提交后会有多条流程实例，全部返回。
     */
    public List<Map<String,Object>> timeline(String businessType,Long businessId){if("REQUISITION".equals(businessType))dataScope.requireRequisition(businessId);else if("PURCHASE_ORDER".equals(businessType))dataScope.requirePurchaseOrder(businessId);else throw new BusinessException(HttpStatus.BAD_REQUEST,"不支持的业务类型");String key=businessType+":"+businessId;var instances=history.createHistoricProcessInstanceQuery().processInstanceBusinessKey(key).orderByProcessInstanceStartTime().asc().list();List<Map<String,Object>> result=new ArrayList<>();for(var p:instances){for(HistoricActivityInstance a:history.createHistoricActivityInstanceQuery().processInstanceId(p.getId()).activityType("userTask").orderByHistoricActivityInstanceStartTime().asc().list()){Map<String,Object> row=new LinkedHashMap<>();row.put("processInstanceId",p.getId());row.put("name",a.getActivityName());row.put("assignee",a.getAssignee());row.put("startTime",a.getStartTime());row.put("endTime",a.getEndTime());row.put("durationInMillis",a.getDurationInMillis());result.add(row);}}return result;}
    /**
     * 审批前的统一守卫，顺序不能乱：任务存在 → 当前用户是候选组或已认领人 → 流程仍活动
     * → 数据范围允许 → 申请人不是自己。任一不满足即抛对应状态码，绝不静默放行。
     */
    private Task requireCandidate(String id){Task t=tasks.createTaskQuery().taskId(id).singleResult();if(t==null)throw new BusinessException(HttpStatus.NOT_FOUND,"审批任务不存在或已处理");CurrentUser u=SecurityUtils.currentUser();boolean allowed=tasks.getIdentityLinksForTask(id).stream().anyMatch(x->u.roles().contains(x.getGroupId()))||u.id().toString().equals(t.getAssignee());if(!allowed)throw new BusinessException(HttpStatus.FORBIDDEN,"该任务不属于当前用户");ProcessInstance process=runtime.createProcessInstanceQuery().processInstanceId(t.getProcessInstanceId()).singleResult();if(process==null)throw new BusinessException(HttpStatus.NOT_FOUND,"审批流程不存在或已结束");dataScope.requireBusinessKey(process.getBusinessKey());String applicant=String.valueOf(runtime.getVariable(t.getProcessInstanceId(),"applicantId"));if(applicant.equals(u.id().toString()))throw new BusinessException(HttpStatus.FORBIDDEN,"申请人不能审批自己的单据");return t;}
    /** 待办过滤用的轻量判断：实例仍存在且业务键在当前用户的数据范围内；每个任务回查一次实例（待办量小，可接受）。 */
    private boolean canAccessTask(Task task){ProcessInstance process=runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();return process!=null&&dataScope.canAccessBusinessKey(process.getBusinessKey());}
    /**
     * 按 businessKey 回写单状态。安全前提：键格式固定为“类型:id”，id 为纯数字且业务键只含一个冒号；
     * 类型未知时按订单处理（当前只有两类业务，新增类型必须同时补分支）。
     */
    private void updateStatus(String key,String status,String reason){String[] p=key.split(":");if("REQUISITION".equals(p[0]))mapper.updateRequisitionStatus(Long.valueOf(p[1]),status,reason,SecurityUtils.currentUser().id());else mapper.updatePurchaseOrderStatus(Long.valueOf(p[1]),status,SecurityUtils.currentUser().id());}
    /** 任务到前端的通用视图；只有运行时任务才补 businessKey（历史任务实例拿不到活动流程）。 */
    private Map<String,Object> taskMap(TaskInfo t){Map<String,Object> row=new LinkedHashMap<>();row.put("id",t.getId());row.put("name",t.getName());row.put("processInstanceId",t.getProcessInstanceId());row.put("createdAt",t.getCreateTime());if(t instanceof Task task){ProcessInstance p=runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();row.put("businessKey",p==null?null:p.getBusinessKey());}return row;}
}
