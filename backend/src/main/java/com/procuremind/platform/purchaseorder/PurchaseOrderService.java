package com.procuremind.platform.purchaseorder;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.file.BusinessAttachmentService;
import com.procuremind.platform.system.SecurityUtils;
import com.procuremind.platform.system.DataScopeService;
import com.procuremind.platform.workflow.WorkflowService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class PurchaseOrderService {
    private final JdbcTemplate jdbc;private final WorkflowService workflow;private final DataScopeService dataScope;private final BusinessAttachmentService attachments;private final com.procuremind.platform.system.DocumentNumberService numbers;private final com.procuremind.platform.budget.BudgetService budget;
    public PurchaseOrderService(JdbcTemplate jdbc,WorkflowService workflow,DataScopeService dataScope,BusinessAttachmentService attachments,com.procuremind.platform.system.DocumentNumberService numbers,com.procuremind.platform.budget.BudgetService budget){this.jdbc=jdbc;this.workflow=workflow;this.dataScope=dataScope;this.attachments=attachments;this.numbers=numbers;this.budget=budget;}
    public List<Map<String,Object>> list(){DataScopeService.SqlFilter filter=dataScope.purchaseOrderFilter("o","u");return jdbc.queryForList("select cast(o.id as char) id,o.order_no orderNo,cast(o.purchaser_id as char) purchaserId,s.name supplier,u.display_name purchaser,o.delivery_date deliveryDate,o.total_amount totalAmount,o.status,o.created_at createdAt,(select count(*) from inv_receipt r where r.purchase_order_id=o.id and r.deleted=0) receiptCount,(select count(*) from fs_business_attachment a where a.business_type='PURCHASE_ORDER' and a.business_id=o.id and a.deleted=0) attachmentCount from po_purchase_order o join sp_supplier s on s.id=o.supplier_id join sys_user u on u.id=o.purchaser_id where o.deleted=0 and "+filter.clause()+" order by o.id desc",filter.args());}
    public Map<String,Object> page(String keyword,String status,int current,int size){
        int page=Math.max(current,1);int pageSize=Math.min(Math.max(size,1),100);
        DataScopeService.SqlFilter filter=dataScope.purchaseOrderFilter("o","u");
        String like=keyword==null||keyword.isBlank()?null:"%"+keyword.trim()+"%";
        String state=status==null||status.isBlank()?null:status.trim().toUpperCase();
        String clause="o.deleted=0 and "+filter.clause()+" and (? is null or o.order_no like ?) and (? is null or o.status=?)";
        List<Object> args=new ArrayList<>(Arrays.asList(filter.args()));
        args.add(like);args.add(like);args.add(state);args.add(state);
        Long total=jdbc.queryForObject("select count(*) from po_purchase_order o join sys_user u on u.id=o.purchaser_id where "+clause,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(pageSize);pageArgs.add((page-1)*pageSize);
        List<Map<String,Object>> records=jdbc.queryForList("select cast(o.id as char) id,o.order_no orderNo,cast(o.purchaser_id as char) purchaserId,s.name supplier,u.display_name purchaser,o.delivery_date deliveryDate,o.total_amount totalAmount,o.status,o.created_at createdAt,(select count(*) from inv_receipt r where r.purchase_order_id=o.id and r.deleted=0) receiptCount from po_purchase_order o join sp_supplier s on s.id=o.supplier_id join sys_user u on u.id=o.purchaser_id where "+clause+" order by o.id desc limit ? offset ?",pageArgs.toArray());
        long totalCount=total==null?0:total;
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("records",records);result.put("total",(int)totalCount);result.put("current",page);result.put("size",pageSize);result.put("pages",(int)((totalCount+pageSize-1)/pageSize));
        return result;
    }

    public Map<String,Object> detail(Long id){dataScope.requirePurchaseOrder(id);Map<String,Object>d=jdbc.queryForMap("select cast(o.id as char) id,o.order_no orderNo,cast(o.requisition_id as char) requisitionId,cast(o.supplier_id as char) supplierId,cast(o.purchaser_id as char) purchaserId,o.delivery_date deliveryDate,o.tax_rate taxRate,o.total_amount totalAmount,o.status,o.terms,o.process_instance_id processInstanceId,o.cancellation_reason cancellationReason,o.closed_at closedAt from po_purchase_order o where o.id=? and o.deleted=0",id);d.put("items",jdbc.queryForList("select cast(i.id as char) id,cast(i.material_id as char) materialId,m.code materialCode,m.name materialName,m.tracking_mode trackingMode,i.quantity,i.received_quantity receivedQuantity,i.unit_price unitPrice,i.tax_rate taxRate,i.remark from po_purchase_order_item i join md_material m on m.id=i.material_id where i.purchase_order_id=? and i.deleted=0",id));return d;}
    @Transactional public Long convert(Long requisitionId,Request r){dataScope.requireRequisition(requisitionId);Map<String,Object> req=jdbc.queryForMap("select status from pr_requisition where id=? and deleted=0 for update",requisitionId);if(!"APPROVED".equals(req.get("status")))throw new BusinessException(HttpStatus.CONFLICT,"只有已审批申请可以转换为订单");if(jdbc.queryForObject("select count(*) from sp_supplier where id=? and status='QUALIFIED' and deleted=0",Long.class,r.supplierId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"供应商未处于合格状态");var items=jdbc.queryForList("select material_id,quantity,estimated_unit_price from pr_requisition_item where requisition_id=? and deleted=0",requisitionId);if(items.isEmpty())throw new BusinessException(HttpStatus.CONFLICT,"申请单没有可转换的明细");Set<String> allowed=new HashSet<>();for(var item:items)allowed.add(item.get("material_id").toString());if(r.priceOverrides()!=null)for(var entry:r.priceOverrides().entrySet())if(!allowed.contains(entry.getKey())||entry.getValue()==null||entry.getValue().signum()<0)throw new BusinessException(HttpStatus.BAD_REQUEST,"订单价格覆盖项无效");String no=numbers.next("PO");BigDecimal total=BigDecimal.ZERO;for(var i:items)total=total.add(((BigDecimal)i.get("quantity")).multiply(r.priceOverrides()!=null&&r.priceOverrides().containsKey(i.get("material_id").toString())?r.priceOverrides().get(i.get("material_id").toString()):(BigDecimal)i.get("estimated_unit_price")));
        Long user=SecurityUtils.currentUser().id();jdbc.update("insert into po_purchase_order(order_no,requisition_id,supplier_id,purchaser_id,delivery_date,tax_rate,total_amount,terms,created_by,updated_by) values(?,?,?,?,?,?,?,?,?,?)",no,requisitionId,r.supplierId(),user,r.deliveryDate(),r.taxRate(),total,r.terms(),user,user);Long id=jdbc.queryForObject("select id from po_purchase_order where order_no=?",Long.class,no);for(var i:items){String material=i.get("material_id").toString();BigDecimal price=r.priceOverrides()!=null&&r.priceOverrides().containsKey(material)?r.priceOverrides().get(material):(BigDecimal)i.get("estimated_unit_price");jdbc.update("insert into po_purchase_order_item(purchase_order_id,material_id,quantity,unit_price,tax_rate,created_by,updated_by) values(?,?,?,?,?,?,?)",id,Long.valueOf(material),i.get("quantity"),price,r.taxRate(),user,user);}jdbc.update("update pr_requisition set status='CONVERTED',updated_by=? where id=?",user,requisitionId);return id;}
    @Transactional public void update(Long id,UpdateRequest r){Map<String,Object> order=orderForUpdate(id);requireOwner(order,true);if(!List.of("DRAFT","REJECTED").contains(order.get("status").toString()))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能编辑");if(r.deliveryDate().isBefore(LocalDate.now()))throw new BusinessException(HttpStatus.BAD_REQUEST,"交付日期不能早于今天");Long supplierCount=jdbc.queryForObject("select count(*) from sp_supplier where id=? and status='QUALIFIED' and deleted=0",Long.class,r.supplierId());if(supplierCount==null||supplierCount==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"供应商未处于合格状态");List<Map<String,Object>> existing=jdbc.queryForList("select id,quantity from po_purchase_order_item where purchase_order_id=? and deleted=0",id);Map<Long,Map<String,Object>> byId=new HashMap<>();for(var item:existing)byId.put(((Number)item.get("id")).longValue(),item);Set<Long> supplied=new HashSet<>();BigDecimal total=BigDecimal.ZERO;for(UpdateItem item:r.items()){if(!supplied.add(item.id())||!byId.containsKey(item.id()))throw new BusinessException(HttpStatus.BAD_REQUEST,"订单明细不匹配");total=total.add(((BigDecimal)byId.get(item.id()).get("quantity")).multiply(item.unitPrice()));}if(supplied.size()!=byId.size())throw new BusinessException(HttpStatus.BAD_REQUEST,"必须提交全部订单明细");Long user=SecurityUtils.currentUser().id();jdbc.update("update po_purchase_order set supplier_id=?,delivery_date=?,tax_rate=?,total_amount=?,terms=?,status='DRAFT',process_instance_id=null,cancellation_reason=null,updated_by=? where id=?",r.supplierId(),r.deliveryDate(),r.taxRate(),total,r.terms(),user,id);for(UpdateItem item:r.items())jdbc.update("update po_purchase_order_item set unit_price=?,tax_rate=?,remark=?,updated_by=? where id=? and purchase_order_id=? and deleted=0",item.unitPrice(),r.taxRate(),item.remark(),user,item.id(),id);}
    @Transactional public void submit(Long id){Map<String,Object> row=jdbc.queryForMap("select purchaser_id,total_amount,status from po_purchase_order where id=? and deleted=0 for update",id);if(!row.get("purchaser_id").toString().equals(SecurityUtils.currentUser().id().toString()))throw new BusinessException(HttpStatus.FORBIDDEN,"只能提交自己创建的订单");if(!List.of("DRAFT","REJECTED").contains(row.get("status").toString()))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能提交审批");String process=workflow.startPurchaseOrder(id,(BigDecimal)row.get("total_amount"),SecurityUtils.currentUser());String nextStatus=workflow.isFinished(process)?"APPROVED":"PENDING_APPROVAL";jdbc.update("update po_purchase_order set status=?,process_instance_id=?,updated_by=? where id=?",nextStatus,process,SecurityUtils.currentUser().id(),id);}
    @Transactional public void cancel(Long id,ReasonRequest r){Map<String,Object> order=orderForUpdate(id);requireOwner(order,true);String status=order.get("status").toString();if(!List.of("DRAFT","REJECTED","PENDING_APPROVAL","APPROVED").contains(status))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能取消");if("APPROVED".equals(status)&&receiptCount(id)>0)throw new BusinessException(HttpStatus.CONFLICT,"已有收货记录的订单不能取消");if("PENDING_APPROVAL".equals(status))workflow.cancelActiveProcesses("PURCHASE_ORDER:"+id,r.reason());Long user=SecurityUtils.currentUser().id();jdbc.update("update po_purchase_order set status='CANCELLED',cancellation_reason=?,process_instance_id=null,updated_by=? where id=?",r.reason().trim(),user,id);restoreSource(order.get("requisition_id"),user);}
    @Transactional public void close(Long id){Map<String,Object> order=orderForUpdate(id);requireOwner(order,true);if(!"RECEIVED".equals(order.get("status")))throw new BusinessException(HttpStatus.CONFLICT,"只有已全部收货订单可以关闭");jdbc.update("update po_purchase_order set status='CLOSED',closed_at=utc_timestamp(6),updated_by=? where id=?",SecurityUtils.currentUser().id(),id);}
    @Transactional public void delete(Long id){Map<String,Object> order=orderForUpdate(id);requireOwner(order,true);if(!List.of("DRAFT","REJECTED","CANCELLED").contains(order.get("status").toString()))throw new BusinessException(HttpStatus.CONFLICT,"只有草稿、驳回或已取消订单可以删除");if(receiptCount(id)>0)throw new BusinessException(HttpStatus.CONFLICT,"已有收货记录的订单不能删除");Long user=SecurityUtils.currentUser().id();jdbc.update("update po_purchase_order_item set deleted=1,updated_by=? where purchase_order_id=? and deleted=0",user,id);jdbc.update("update po_purchase_order set deleted=1,updated_by=? where id=?",user,id);attachments.unlinkBusiness("PURCHASE_ORDER",id);restoreSource(order.get("requisition_id"),user);}
    private Map<String,Object> orderForUpdate(Long id){List<Map<String,Object>> rows=jdbc.queryForList("select purchaser_id,requisition_id,supplier_id,status from po_purchase_order where id=? and deleted=0 for update",id);if(rows.isEmpty())throw new BusinessException(HttpStatus.NOT_FOUND,"采购订单不存在");return rows.getFirst();}
    private void requireOwner(Map<String,Object> order,boolean adminAllowed){var user=SecurityUtils.currentUser();if(!order.get("purchaser_id").toString().equals(user.id().toString())&&!(adminAllowed&&user.roles().contains("ADMIN")))throw new BusinessException(HttpStatus.FORBIDDEN,"只能操作自己创建的订单");}
    private long receiptCount(Long id){Long count=jdbc.queryForObject("select count(*) from inv_receipt where purchase_order_id=? and deleted=0",Long.class,id);return count==null?0:count;}
    // ==================== 直采订单（供 Agent / 内部接口使用） ====================

    @Transactional public Map<String,Object> createDirect(DirectOrderRequest request){
        var currentUser=SecurityUtils.currentUser();Long user=currentUser.id();
        Long supplierId=request.supplierId()==null?defaultSupplier():request.supplierId();
        requireQualifiedSupplier(supplierId);
        List<DirectItem> items=requireDirectItems(request.items());
        LocalDate delivery=request.deliveryDate()==null?LocalDate.now().plusDays(7):request.deliveryDate();
        if(delivery.isBefore(LocalDate.now()))throw badRequest("交付日期不能早于今天");
        BigDecimal taxRate=request.taxRate()==null?new BigDecimal("0.13"):request.taxRate();
        if(taxRate.signum()<0||taxRate.compareTo(BigDecimal.ONE)>0)throw badRequest("税率必须在 0~1 之间");
        BigDecimal total=BigDecimal.ZERO;
        for(DirectItem item:items)total=total.add(item.quantity().multiply(item.unitPrice()));
        budget.check(currentUser.departmentId(),total);
        String no=numbers.next("PO");
        jdbc.update("insert into po_purchase_order(order_no,requisition_id,supplier_id,purchaser_id,delivery_date,tax_rate,total_amount,terms,created_by,updated_by) values(?,null,?,?,?,?,?,?,?,?)",
                no,supplierId,user,delivery,taxRate,total,blank(request.terms()),user,user);
        Long id=jdbc.queryForObject("select id from po_purchase_order where order_no=?",Long.class,no);
        for(DirectItem item:items)jdbc.update("insert into po_purchase_order_item(purchase_order_id,material_id,quantity,unit_price,tax_rate,remark,created_by,updated_by) values(?,?,?,?,?,?,?,?)",
                id,item.partId(),item.quantity(),item.unitPrice(),taxRate,blank(item.remark()),user,user);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",String.valueOf(id));
        result.put("orderNo",no);
        result.put("status","DRAFT");
        result.put("totalAmount",total.toPlainString());
        return result;
    }

    @Transactional public void updateDirect(Long id,DirectOrderRequest request){
        Map<String,Object> order=orderForUpdate(id);
        requireOwner(order,true);
        if(!List.of("DRAFT","REJECTED").contains(order.get("status").toString()))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能编辑");
        Long user=SecurityUtils.currentUser().id();
        Long supplierId=request.supplierId()==null?((Number)order.get("supplier_id")).longValue():request.supplierId();
        requireQualifiedSupplier(supplierId);
        List<DirectItem> items=requireDirectItems(request.items());
        LocalDate delivery=request.deliveryDate()==null?LocalDate.now().plusDays(7):request.deliveryDate();
        if(delivery.isBefore(LocalDate.now()))throw badRequest("交付日期不能早于今天");
        BigDecimal taxRate=request.taxRate()==null?new BigDecimal("0.13"):request.taxRate();
        if(taxRate.signum()<0||taxRate.compareTo(BigDecimal.ONE)>0)throw badRequest("税率必须在 0~1 之间");
        BigDecimal total=BigDecimal.ZERO;
        for(DirectItem item:items)total=total.add(item.quantity().multiply(item.unitPrice()));
        jdbc.update("update po_purchase_order_item set deleted=1,updated_by=? where purchase_order_id=? and deleted=0",user,id);
        for(DirectItem item:items)jdbc.update("insert into po_purchase_order_item(purchase_order_id,material_id,quantity,unit_price,tax_rate,remark,created_by,updated_by) values(?,?,?,?,?,?,?,?)",
                id,item.partId(),item.quantity(),item.unitPrice(),taxRate,blank(item.remark()),user,user);
        jdbc.update("update po_purchase_order set supplier_id=?,delivery_date=?,tax_rate=?,total_amount=?,terms=?,status='DRAFT',process_instance_id=null,updated_by=? where id=?",
                supplierId,delivery,taxRate,total,blank(request.terms()),user,id);
    }

    private Long defaultSupplier(){
        List<Long> ids=jdbc.queryForList("select id from sp_supplier where status='QUALIFIED' and deleted=0 order by score desc,id limit 1",Long.class);
        if(ids.isEmpty())throw badRequest("没有可用的合格供应商");
        return ids.getFirst();
    }

    private void requireQualifiedSupplier(Long supplierId){
        Long count=jdbc.queryForObject("select count(*) from sp_supplier where id=? and status='QUALIFIED' and deleted=0",Long.class,supplierId);
        if(count==null||count==0)throw badRequest("供应商未处于合格状态");
    }

    private List<DirectItem> requireDirectItems(List<DirectItem> items){
        if(items==null||items.isEmpty())throw badRequest("订单明细不能为空");
        Set<Long> seen=new HashSet<>();
        for(DirectItem item:items){
            if(item==null||item.partId()==null)throw badRequest("订单明细缺少物资");
            if(!seen.add(item.partId()))throw badRequest("同一物资不能重复添加");
            if(item.quantity()==null||item.quantity().signum()<=0)throw badRequest("数量必须大于 0");
            if(item.unitPrice()==null||item.unitPrice().signum()<0)throw badRequest("单价不能为负数");
            Long exists=jdbc.queryForObject("select count(*) from md_material where id=? and enabled=1 and deleted=0",Long.class,item.partId());
            if(exists==null||exists==0)throw badRequest("物资不存在或已停用："+item.partId());
        }
        return items;
    }

    private BusinessException badRequest(String message){return new BusinessException(HttpStatus.BAD_REQUEST,message);}

    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}

    private void restoreSource(Object requisitionId,Long user){if(requisitionId==null)return;Long active=jdbc.queryForObject("select count(*) from po_purchase_order where requisition_id=? and deleted=0 and status<>'CANCELLED'",Long.class,requisitionId);if(active==null||active==0)jdbc.update("update pr_requisition set status='APPROVED',updated_by=? where id=? and deleted=0 and status='CONVERTED'",user,requisitionId);}
    /** 物资历史成交价：按当前用户的数据范围返回最近 50 条订单明细。 */
    public List<Map<String,Object>> priceHistory(Long materialId){DataScopeService.SqlFilter filter=dataScope.purchaseOrderFilter("o","u");List<Object> args=new ArrayList<>();args.add(materialId);args.addAll(Arrays.asList(filter.args()));return jdbc.queryForList("select cast(o.id as char) orderId,o.order_no orderNo,s.name supplierName,i.unit_price unitPrice,i.quantity quantity,o.created_at createdAt from po_purchase_order_item i join po_purchase_order o on o.id=i.purchase_order_id join sp_supplier s on s.id=o.supplier_id join sys_user u on u.id=o.purchaser_id where i.material_id=? and i.deleted=0 and o.deleted=0 and "+filter.clause()+" order by o.created_at desc limit 50",args.toArray());}

    public record Request(@NotNull Long supplierId,@NotNull @FutureOrPresent LocalDate deliveryDate,@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,@Size(max=1000) String terms,Map<String,BigDecimal> priceOverrides){}    public record UpdateRequest(@NotNull Long supplierId,@NotNull @FutureOrPresent LocalDate deliveryDate,@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,@Size(max=1000) String terms,@NotEmpty @Size(max=100) List<@Valid UpdateItem> items){}
    public record UpdateItem(@NotNull Long id,@NotNull @DecimalMin("0") BigDecimal unitPrice,@Size(max=500) String remark){}
    public record ReasonRequest(@NotBlank @Size(max=500) String reason){}
    public record DirectOrderRequest(Long supplierId,@FutureOrPresent LocalDate deliveryDate,@DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,@Size(max=1000) String terms,@NotEmpty @Size(max=100) List<@Valid DirectItem> items){}
    public record DirectItem(@NotNull Long partId,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity,@NotNull @DecimalMin("0") BigDecimal unitPrice,@Size(max=500) String remark){}
}
