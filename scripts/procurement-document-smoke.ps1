param([string]$BaseUrl = "http://localhost:3000")
$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition,[string]$Message){if(-not $Condition){throw "第四批验收失败：$Message"}}
function Login([string]$Username,[string]$Password){$r=Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body (@{username=$Username;password=$Password}|ConvertTo-Json);Assert-True ($r.code -eq 0) "$Username 登录失败";return $r.data.accessToken}
function Auth-Headers([string]$Token){@{Authorization="Bearer $Token"}}
function Get-Data([string]$Path,[string]$Token){$r=Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1$Path" -Headers (Auth-Headers $Token);Assert-True ($r.code -eq 0) "GET $Path 失败";return $r.data}
function Send([string]$Method,[string]$Path,[string]$Token,$Body=$null){$p=@{Method=$Method;Uri="$BaseUrl/api/v1$Path";Headers=(Auth-Headers $Token)};if($null-ne $Body){$p.ContentType="application/json";$p.Body=$Body|ConvertTo-Json -Depth 12};$r=Invoke-RestMethod @p;Assert-True ($r.code -eq 0) "$Method $Path 失败";return $r.data}
function Expect([string]$Method,[string]$Path,[string]$Token,[int]$Status,$Body=$null){try{$p=@{Method=$Method;Uri="$BaseUrl/api/v1$Path";Headers=(Auth-Headers $Token);UseBasicParsing=$true};if($null-ne $Body){$p.ContentType="application/json";$p.Body=$Body|ConvertTo-Json -Depth 12};Invoke-WebRequest @p|Out-Null;throw "未返回预期 HTTP $Status"}catch{if($_.Exception.Response-and [int]$_.Exception.Response.StatusCode-eq $Status){return};throw}}
function Task([string]$Token,[string]$Key){return @(Get-Data "/workflow/tasks" $Token|Where-Object{$_.businessKey-eq $Key})|Select-Object -First 1}
function Approve([string]$Token,[string]$Key){$t=Task $Token $Key;Assert-True ($null-ne $t) "未找到待办 $Key";Send Post "/workflow/$($t.id)/approve" $Token @{comment="第四批自动验收通过"}|Out-Null}

$token=@{
  requester=Login requester 'Request@123';manager=Login manager 'Manager@123';purchaser=Login purchaser 'Purchase@123';viewer=Login viewer 'Viewer@123';warehouse=Login warehouse 'Warehouse@123'
}
$material=@(Get-Data '/materials' $token.requester)|Where-Object{$_.trackingMode-eq 'QUANTITY'}|Select-Object -First 1
$supplier=@(Get-Data '/suppliers' $token.purchaser)|Where-Object{$_.status-eq 'QUALIFIED'}|Select-Object -First 1
Assert-True ($null-ne $material) '缺少数量型物资';Assert-True ($null-ne $supplier) '缺少合格供应商'

# V8 固定展示数据应由 Flyway 写入。
$fixedReq=@(Get-Data '/requisitions' $token.requester)|Where-Object{$_.requisitionNo-eq 'PR-V8-DRAFT'}
$fixedOrder=@(Get-Data '/purchase-orders' $token.purchaser)|Where-Object{$_.orderNo-eq 'PO-V8-DRAFT'}
Assert-True ($fixedReq.Count-eq 1) '缺少第四批固定申请';Assert-True ($fixedOrder.Count-eq 1) '缺少第四批固定订单'

$body=@{requiredDate=(Get-Date).AddDays(20).ToString('yyyy-MM-dd');purpose='[第四批验收] 申请取消、附件和删除';items=@(@{materialId=$material.id;quantity='2';estimatedUnitPrice='900';remark='第四批'})}
$reqId=(Send Post '/requisitions' $token.requester $body).id

# 真实执行预签名 PUT、关联和下载授权。
$bytes=[Text.Encoding]::UTF8.GetBytes('ProcureMind fourth batch attachment')
$signed=Send Post '/files/presign' $token.requester @{fileName='fourth-batch.txt';contentType='application/octet-stream';size=$bytes.Length}
Invoke-WebRequest -Method Put -Uri $signed.uploadUrl -ContentType 'application/octet-stream' -Body $bytes -UseBasicParsing|Out-Null
$attachmentId=(Send Post "/files/attachments/REQUISITION/$reqId" $token.requester @{fileId=$signed.fileId;category='REQUEST';note='第四批验收附件'}).id
$attachment=@(Get-Data "/files/attachments/REQUISITION/$reqId" $token.requester)
Assert-True ($attachment.Count-eq 1) '申请附件关联失败'
$download=Get-Data "/files/$($signed.fileId)" $token.requester
Assert-True ($download.downloadUrl.StartsWith('http')) '未生成附件下载地址'

Send Post "/requisitions/$reqId/submit" $token.requester|Out-Null
Assert-True ($null-ne (Task $token.manager "REQUISITION:$reqId")) '申请未进入审批流程'
Send Patch "/requisitions/$reqId/cancel" $token.requester @{reason='自动化验证审批中取消'}|Out-Null
$cancelled=Get-Data "/requisitions/$reqId" $token.requester
Assert-True ($cancelled.status-eq 'CANCELLED') '申请取消状态错误'
Assert-True ($null-eq (Task $token.manager "REQUISITION:$reqId")) '取消后仍存在活动待办'
Send Delete "/requisitions/$reqId" $token.requester|Out-Null
Expect Get "/requisitions/$reqId" $token.requester 404
Send Delete "/files/$($signed.fileId)" $token.requester|Out-Null

# 新建、审批、转订单，验证编辑、附件、权限、取消、删除和来源状态恢复。
$sourceId=(Send Post '/requisitions' $token.requester (@{requiredDate=(Get-Date).AddDays(21).ToString('yyyy-MM-dd');purpose='[第四批验收] 订单生命周期';items=@(@{materialId=$material.id;quantity='1';estimatedUnitPrice='1200';remark='订单编辑'})})).id
Send Post "/requisitions/$sourceId/submit" $token.requester|Out-Null
$key="REQUISITION:$sourceId";Approve $token.manager $key;Approve $token.purchaser $key
$orderId=(Send Post "/requisitions/$sourceId/convert" $token.purchaser @{supplierId=$supplier.id;deliveryDate=(Get-Date).AddDays(30).ToString('yyyy-MM-dd');taxRate='0.13';terms='编辑前条款';priceOverrides=@{}}).id
$order=Get-Data "/purchase-orders/$orderId" $token.purchaser
$update=@{supplierId=$supplier.id;deliveryDate=(Get-Date).AddDays(35).ToString('yyyy-MM-dd');taxRate='0.09';terms='[第四批验收] 编辑后条款';items=@($order.items|ForEach-Object{@{id=$_.id;unitPrice='1300';remark='已编辑'}})}
Expect Put "/purchase-orders/$orderId" $token.viewer 403 $update
Send Put "/purchase-orders/$orderId" $token.purchaser $update|Out-Null
$updated=Get-Data "/purchase-orders/$orderId" $token.purchaser
Assert-True ($updated.terms-eq '[第四批验收] 编辑后条款') '订单条款未更新';Assert-True ([decimal]$updated.totalAmount-eq 1300) '订单总额未重算'

$orderSigned=Send Post '/files/presign' $token.purchaser @{fileName='quotation-fourth.txt';contentType='application/octet-stream';size=$bytes.Length}
Invoke-WebRequest -Method Put -Uri $orderSigned.uploadUrl -ContentType 'application/octet-stream' -Body $bytes -UseBasicParsing|Out-Null
Send Post "/files/attachments/PURCHASE_ORDER/$orderId" $token.purchaser @{fileId=$orderSigned.fileId;category='QUOTATION';note='订单报价'}|Out-Null
Send Patch "/purchase-orders/$orderId/cancel" $token.purchaser @{reason='验证订单取消'}|Out-Null
$restoredAfterCancel=Get-Data "/requisitions/$sourceId" $token.requester
Assert-True ($restoredAfterCancel.status-eq 'APPROVED') '取消订单后来源申请未恢复为已批准'
Send Delete "/purchase-orders/$orderId" $token.purchaser|Out-Null
$source=Get-Data "/requisitions/$sourceId" $token.requester
Assert-True ($source.status-eq 'APPROVED') '删除订单后来源申请未恢复为已批准'
Send Delete "/files/$($orderSigned.fileId)" $token.purchaser|Out-Null
Send Patch "/requisitions/$sourceId/cancel" $token.requester @{reason='清理第四批验收数据'}|Out-Null
Send Delete "/requisitions/$sourceId" $token.requester|Out-Null

$receipt=@(Get-Data '/receipts' $token.warehouse)|Select-Object -First 1
if($receipt){$receiptDetail=Get-Data "/receipts/$($receipt.id)" $token.warehouse;Assert-True ($receiptDetail.items.Count-ge 1) '收货详情缺少明细'}

[pscustomobject]@{result='PASS';fixedDemoRequisition='PR-V8-DRAFT';fixedDemoOrder='PO-V8-DRAFT';requisitionCancelDelete='PASS';workflowCancellation='PASS';orderUpdateCancelDelete='PASS';attachmentUploadDownload='PASS';sourceStatusRestore='PASS';receiptDetail=if($receipt){'PASS'}else{'SKIPPED_NO_RECEIPT'}}|ConvertTo-Json -Depth 4
