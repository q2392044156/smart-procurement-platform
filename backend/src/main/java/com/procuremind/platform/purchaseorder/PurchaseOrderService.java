package com.procuremind.platform.purchaseorder;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import com.procuremind.platform.system.DataScopeService;
import com.procuremind.platform.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseOrderService {
    private final JdbcTemplate jdbc;private final WorkflowService workflow;private final DataScopeService dataScope;
    public PurchaseOrderService(JdbcTemplate jdbc,WorkflowService workflow,DataScopeService dataScope){this.jdbc=jdbc;this.workflow=workflow;this.dataScope=dataScope;}
    public List<Map<String,Object>> list(){DataScopeService.SqlFilter filter=dataScope.purchaseOrderFilter("o","u");return jdbc.queryForList("select cast(o.id as char) id,o.order_no orderNo,s.name supplier,u.display_name purchaser,o.delivery_date deliveryDate,o.total_amount totalAmount,o.status,o.created_at createdAt from po_purchase_order o join sp_supplier s on s.id=o.supplier_id join sys_user u on u.id=o.purchaser_id where o.deleted=0 and "+filter.clause()+" order by o.id desc",filter.args());}
    public Map<String,Object> detail(Long id){dataScope.requirePurchaseOrder(id);Map<String,Object>d=jdbc.queryForMap("select cast(o.id as char) id,o.order_no orderNo,cast(o.requisition_id as char) requisitionId,cast(o.supplier_id as char) supplierId,cast(o.purchaser_id as char) purchaserId,o.delivery_date deliveryDate,o.tax_rate taxRate,o.total_amount totalAmount,o.status,o.terms,o.process_instance_id processInstanceId from po_purchase_order o where o.id=? and o.deleted=0",id);d.put("items",jdbc.queryForList("select cast(i.id as char) id,cast(i.material_id as char) materialId,m.code materialCode,m.name materialName,m.tracking_mode trackingMode,i.quantity,i.received_quantity receivedQuantity,i.unit_price unitPrice,i.tax_rate taxRate,i.remark from po_purchase_order_item i join md_material m on m.id=i.material_id where i.purchase_order_id=? and i.deleted=0",id));return d;}
    @Transactional public Long convert(Long requisitionId,Request r){Map<String,Object> req=jdbc.queryForMap("select status from pr_requisition where id=? for update",requisitionId);if(!"APPROVED".equals(req.get("status")))throw new BusinessException(HttpStatus.CONFLICT,"只有已审批申请可以转换为订单");if(jdbc.queryForObject("select count(*) from sp_supplier where id=? and status='QUALIFIED' and deleted=0",Long.class,r.supplierId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"供应商未处于合格状态");var items=jdbc.queryForList("select material_id,quantity,estimated_unit_price from pr_requisition_item where requisition_id=? and deleted=0",requisitionId);String no="PO"+System.currentTimeMillis();BigDecimal total=BigDecimal.ZERO;for(var i:items)total=total.add(((BigDecimal)i.get("quantity")).multiply(r.priceOverrides()!=null&&r.priceOverrides().containsKey(i.get("material_id").toString())?r.priceOverrides().get(i.get("material_id").toString()):(BigDecimal)i.get("estimated_unit_price")));
        Long user=SecurityUtils.currentUser().id();jdbc.update("insert into po_purchase_order(order_no,requisition_id,supplier_id,purchaser_id,delivery_date,tax_rate,total_amount,terms,created_by,updated_by) values(?,?,?,?,?,?,?,?,?,?)",no,requisitionId,r.supplierId(),user,r.deliveryDate(),r.taxRate(),total,r.terms(),user,user);Long id=jdbc.queryForObject("select id from po_purchase_order where order_no=?",Long.class,no);for(var i:items){String material=i.get("material_id").toString();BigDecimal price=r.priceOverrides()!=null&&r.priceOverrides().containsKey(material)?r.priceOverrides().get(material):(BigDecimal)i.get("estimated_unit_price");jdbc.update("insert into po_purchase_order_item(purchase_order_id,material_id,quantity,unit_price,tax_rate,created_by,updated_by) values(?,?,?,?,?,?,?)",id,Long.valueOf(material),i.get("quantity"),price,r.taxRate(),user,user);}jdbc.update("update pr_requisition set status='CONVERTED',updated_by=? where id=?",user,requisitionId);return id;}
    @Transactional public void submit(Long id){Map<String,Object> row=jdbc.queryForMap("select purchaser_id,total_amount,status from po_purchase_order where id=? and deleted=0 for update",id);if(!row.get("purchaser_id").toString().equals(SecurityUtils.currentUser().id().toString()))throw new BusinessException(HttpStatus.FORBIDDEN,"只能提交自己创建的订单");if(!List.of("DRAFT","REJECTED").contains(row.get("status").toString()))throw new BusinessException(HttpStatus.CONFLICT,"当前状态不能提交审批");String process=workflow.startPurchaseOrder(id,(BigDecimal)row.get("total_amount"),SecurityUtils.currentUser());String nextStatus=workflow.isFinished(process)?"APPROVED":"PENDING_APPROVAL";jdbc.update("update po_purchase_order set status=?,process_instance_id=?,updated_by=? where id=?",nextStatus,process,SecurityUtils.currentUser().id(),id);}
    public record Request(Long supplierId,LocalDate deliveryDate,BigDecimal taxRate,String terms,Map<String,BigDecimal> priceOverrides){}
}
