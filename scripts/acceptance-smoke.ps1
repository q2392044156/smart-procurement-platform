param(
    [string]$BaseUrl = "http://localhost:3000"
)

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "验收失败：$Message" }
}

function Login([string]$Username, [string]$Password) {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body $body
    Assert-True ($response.code -eq 0) "$Username 登录失败"
    return $response.data.accessToken
}

function Headers([string]$Token) {
    return @{ Authorization = "Bearer $Token" }
}

function Get-Data([string]$Path, [string]$Token) {
    $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1$Path" -Headers (Headers $Token)
    Assert-True ($response.code -eq 0) "GET $Path 返回业务错误"
    return $response.data
}

function Send-Data([string]$Method, [string]$Path, [string]$Token, $Body = $null, [hashtable]$ExtraHeaders = @{}) {
    $headers = Headers $Token
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $parameters = @{ Method = $Method; Uri = "$BaseUrl/api/v1$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 12
    }
    $response = Invoke-RestMethod @parameters
    Assert-True ($response.code -eq 0) "$Method $Path 返回业务错误"
    return $response.data
}

function Expect-Status([string]$Method, [string]$Path, [string]$Token, [int]$Status, $Body = $null, [hashtable]$ExtraHeaders = @{}) {
    $headers = Headers $Token
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    try {
        $parameters = @{ Method = $Method; Uri = "$BaseUrl/api/v1$Path"; Headers = $headers }
        if ($null -ne $Body) {
            $parameters.ContentType = "application/json"
            $parameters.Body = $Body | ConvertTo-Json -Depth 12
        }
        Invoke-WebRequest @parameters -UseBasicParsing | Out-Null
        throw "接口未按预期返回 HTTP $Status：$Method $Path"
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Status) { return }
        throw
    }
}

function Task-For([string]$Token, [string]$BusinessKey) {
    return @(Get-Data "/workflow/tasks" $Token | Where-Object { $_.businessKey -eq $BusinessKey }) | Select-Object -First 1
}

function Approve-For([string]$Token, [string]$BusinessKey, [string]$Comment) {
    $task = Task-For $Token $BusinessKey
    Assert-True ($null -ne $task) "未找到待办 $BusinessKey"
    Send-Data "Post" "/workflow/$($task.id)/approve" $Token @{ comment = $Comment } | Out-Null
}

function New-Requisition([string]$Token, [string]$MaterialId, [decimal]$Quantity, [decimal]$UnitPrice, [string]$Purpose) {
    $payload = @{
        requiredDate = (Get-Date).AddDays(14).ToString("yyyy-MM-dd")
        purpose = $Purpose
        items = @(@{ materialId = $MaterialId; quantity = "$Quantity"; estimatedUnitPrice = "$UnitPrice"; remark = "自动化验收" })
    }
    return (Send-Data "Post" "/requisitions" $Token $payload).id
}

function New-Approved-Order($Material, [decimal]$Quantity, [decimal]$UnitPrice, [string]$Purpose) {
    $requisitionId = New-Requisition $tokens.requester $Material.id $Quantity $UnitPrice $Purpose
    Send-Data "Post" "/requisitions/$requisitionId/submit" $tokens.requester | Out-Null
    $requisitionKey = "REQUISITION:$requisitionId"
    Approve-For $tokens.manager $requisitionKey "部门审批通过"
    Approve-For $tokens.purchaser $requisitionKey "采购审批通过"
    $orderBody = @{
        supplierId = $supplier.id
        deliveryDate = (Get-Date).AddDays(30).ToString("yyyy-MM-dd")
        taxRate = "0.13"
        terms = "库存跟踪自动化验收"
        priceOverrides = @{}
    }
    $purchaseOrderId = (Send-Data "Post" "/requisitions/$requisitionId/convert" $tokens.purchaser $orderBody).id
    Send-Data "Post" "/purchase-orders/$purchaseOrderId/submit" $tokens.purchaser | Out-Null
    Approve-For $tokens.finance "PURCHASE_ORDER:$purchaseOrderId" "商业条款通过"
    return Get-Data "/purchase-orders/$purchaseOrderId" $tokens.purchaser
}

$tokens = @{
    admin = Login "admin" "Admin@123"
    requester = Login "requester" "Request@123"
    manager = Login "manager" "Manager@123"
    purchaser = Login "purchaser" "Purchase@123"
    finance = Login "finance" "Finance@123"
    warehouse = Login "warehouse" "Warehouse@123"
    gm = Login "gm" "Gm@123"
}

$materials = @(Get-Data "/materials" $tokens.requester)
$material = $materials | Where-Object { $_.trackingMode -eq "QUANTITY" } | Select-Object -First 1
$batchMaterial = $materials | Where-Object { $_.trackingMode -eq "BATCH" } | Select-Object -First 1
$serialMaterial = $materials | Where-Object { $_.trackingMode -eq "SERIAL" } | Select-Object -First 1
$warehouse = @(Get-Data "/warehouses" $tokens.warehouse) | Select-Object -First 1
$supplier = @(Get-Data "/suppliers" $tokens.purchaser | Where-Object { $_.status -eq "QUALIFIED" }) | Select-Object -First 1
Assert-True ($null -ne $material) "缺少数量型演示物资"
Assert-True ($null -ne $batchMaterial) "缺少批次型演示物资"
Assert-True ($null -ne $serialMaterial) "缺少序列号型演示物资"
Assert-True ($null -ne $warehouse) "缺少演示仓库"
Assert-True ($null -ne $supplier) "缺少合格供应商"

# 前后端都隐藏菜单之外，后端必须执行最终权限检查。
Expect-Status "Get" "/purchase-orders" $tokens.requester 403

# ALL、DEPARTMENT、SELF 三种数据范围必须在后端生效。
$adminDraftId = New-Requisition $tokens.admin $material.id 1 99 "数据范围隔离验收"
$requesterIds = @(Get-Data "/requisitions" $tokens.requester).id
$managerIds = @(Get-Data "/requisitions" $tokens.manager).id
$purchaserIds = @(Get-Data "/requisitions" $tokens.purchaser).id
Assert-True ($adminDraftId -notin $requesterIds) "SELF 数据范围看到了其他申请人的单据"
Assert-True ($adminDraftId -notin $managerIds) "DEPARTMENT 数据范围看到了其他部门单据"
Assert-True ($adminDraftId -in $purchaserIds) "ALL 数据范围未看到全企业单据"

# 三档金额分别验证审批链：低金额无财务，中金额有财务，高金额增加总经理。
$lowId = New-Requisition $tokens.requester $material.id 4 1000 "低金额采购与收货验收"
Send-Data "Post" "/requisitions/$lowId/submit" $tokens.requester | Out-Null
$lowKey = "REQUISITION:$lowId"
Approve-For $tokens.manager $lowKey "部门预算合理"
Approve-For $tokens.purchaser $lowKey "采购方案可执行"
$low = Get-Data "/requisitions/$lowId" $tokens.requester
Assert-True ($low.status -eq "APPROVED") "低金额申请未在采购审批后结束"
Assert-True ($null -eq (Task-For $tokens.finance $lowKey)) "低金额申请不应进入财务审批"

$middleId = New-Requisition $tokens.requester $material.id 1 60000 "中金额审批链验收"
Send-Data "Post" "/requisitions/$middleId/submit" $tokens.requester | Out-Null
$middleKey = "REQUISITION:$middleId"
Approve-For $tokens.manager $middleKey "部门同意"
Approve-For $tokens.purchaser $middleKey "采购同意"
Approve-For $tokens.finance $middleKey "财务预算通过"
$middle = Get-Data "/requisitions/$middleId" $tokens.requester
Assert-True ($middle.status -eq "APPROVED") "中金额申请未在财务审批后结束"
Assert-True ($null -eq (Task-For $tokens.gm $middleKey)) "中金额申请不应进入总经理审批"

$highId = New-Requisition $tokens.requester $material.id 1 600000 "高金额审批链验收"
Send-Data "Post" "/requisitions/$highId/submit" $tokens.requester | Out-Null
$highKey = "REQUISITION:$highId"
Approve-For $tokens.manager $highKey "部门同意"
Approve-For $tokens.purchaser $highKey "采购同意"
Approve-For $tokens.finance $highKey "财务同意"
Approve-For $tokens.gm $highKey "总经理同意"
$high = Get-Data "/requisitions/$highId" $tokens.requester
Assert-True ($high.status -eq "APPROVED") "高金额申请未经过完整审批链"

# 驳回后修改、重新提交，历史中必须存在两个流程实例。
$retryId = New-Requisition $tokens.requester $material.id 1 1200 "驳回重提验收"
Send-Data "Post" "/requisitions/$retryId/submit" $tokens.requester | Out-Null
$retryKey = "REQUISITION:$retryId"
$rejectTask = Task-For $tokens.manager $retryKey
Assert-True ($null -ne $rejectTask) "驳回场景未生成部门经理待办"
Send-Data "Post" "/workflow/$($rejectTask.id)/reject" $tokens.manager @{ comment = "请补充交付用途" } | Out-Null
$rejected = Get-Data "/requisitions/$retryId" $tokens.requester
Assert-True ($rejected.status -eq "REJECTED") "申请驳回状态错误"
$retryBody = @{
    requiredDate = (Get-Date).AddDays(21).ToString("yyyy-MM-dd")
    purpose = "已补充交付用途的驳回重提验收"
    items = @(@{ materialId = $material.id; quantity = "1"; estimatedUnitPrice = "1200"; remark = "已补充" })
}
Send-Data "Put" "/requisitions/$retryId" $tokens.requester $retryBody | Out-Null
Send-Data "Post" "/requisitions/$retryId/submit" $tokens.requester | Out-Null
Approve-For $tokens.manager $retryKey "补充完整"
Approve-For $tokens.purchaser $retryKey "采购通过"
$timeline = @(Get-Data "/workflow/timeline/REQUISITION/$retryId" $tokens.requester)
$instanceCount = @($timeline.processInstanceId | Sort-Object -Unique).Count
Assert-True ($instanceCount -ge 2) "驳回重提未保留两次流程历史"

# 已审批申请转订单，订单审批后执行两次收货与幂等重放。
$orderBody = @{
    supplierId = $supplier.id
    deliveryDate = (Get-Date).AddDays(30).ToString("yyyy-MM-dd")
    taxRate = "0.13"
    terms = "验收合格后30日付款"
    priceOverrides = @{}
}
$orderId = (Send-Data "Post" "/requisitions/$lowId/convert" $tokens.purchaser $orderBody).id
Send-Data "Post" "/purchase-orders/$orderId/submit" $tokens.purchaser | Out-Null
$orderKey = "PURCHASE_ORDER:$orderId"
Approve-For $tokens.finance $orderKey "商业条款通过"
$order = Get-Data "/purchase-orders/$orderId" $tokens.purchaser
Assert-True ($order.status -eq "APPROVED") "订单未审批通过"
$orderItem = @($order.items) | Select-Object -First 1

$balanceBefore = @(Get-Data "/inventory/balances" $tokens.warehouse | Where-Object { $_.materialCode -eq $material.code -and $_.warehouse -eq $warehouse.name }) | Select-Object -First 1
$beforeQuantity = if ($balanceBefore) { [decimal]$balanceBefore.quantity } else { [decimal]0 }
$receiptBody = @{
    purchaseOrderId = $orderId
    warehouseId = $warehouse.id
    remark = "第一次分批收货"
    items = @(@{ orderItemId = $orderItem.id; quantity = "2"; batchNo = $null; serialNumbers = @() })
}
$overReceiptBody = @{
    purchaseOrderId = $orderId
    warehouseId = $warehouse.id
    remark = "超量收货应被拒绝"
    items = @(@{ orderItemId = $orderItem.id; quantity = "5"; batchNo = $null; serialNumbers = @() })
}
Expect-Status "Post" "/receipts" $tokens.warehouse 409 $overReceiptBody @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
$idempotencyKey = [guid]::NewGuid().ToString()
$firstReceipt = Send-Data "Post" "/receipts" $tokens.warehouse $receiptBody @{ "Idempotency-Key" = $idempotencyKey }
$replayedReceipt = Send-Data "Post" "/receipts" $tokens.warehouse $receiptBody @{ "Idempotency-Key" = $idempotencyKey }
Assert-True ($firstReceipt.id -eq $replayedReceipt.id) "相同幂等键生成了重复收货单"
Assert-True ($firstReceipt.orderStatus -eq "PARTIALLY_RECEIVED") "第一次收货后订单不是部分收货"

$receiptBody.remark = "第二次分批收货"
$secondReceipt = Send-Data "Post" "/receipts" $tokens.warehouse $receiptBody @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
Assert-True ($secondReceipt.orderStatus -eq "RECEIVED") "第二次收货后订单不是全部收货"
Expect-Status "Post" "/receipts" $tokens.warehouse 409 $receiptBody @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }

$balanceAfter = @(Get-Data "/inventory/balances" $tokens.warehouse | Where-Object { $_.materialCode -eq $material.code -and $_.warehouse -eq $warehouse.name }) | Select-Object -First 1
Assert-True ([decimal]$balanceAfter.quantity -eq ($beforeQuantity + 4)) "库存余额与两次收货数量不一致"
$receipts = @(Get-Data "/receipts" $tokens.warehouse | Where-Object { $_.orderNo -eq $order.orderNo })
Assert-True ($receipts.Count -eq 2) "幂等重放后收货单数量不是2"
$receiptDetail = Get-Data "/receipts/$($receipts[0].id)" $tokens.warehouse
Assert-True (@($receiptDetail.items).Count -eq 1) "收货单详情未返回收货明细"
Send-Data "Patch" "/purchase-orders/$orderId/close" $tokens.purchaser | Out-Null
$closedOrder = Get-Data "/purchase-orders/$orderId" $tokens.purchaser
Assert-True ($closedOrder.status -eq "CLOSED") "已全部收货订单未能正常关闭"

# 批次物资必须填写批次号；合法批次收货后正常入账。
$batchOrder = New-Approved-Order $batchMaterial 2 100 "批次跟踪验收"
$batchItem = @($batchOrder.items) | Select-Object -First 1
$batchReceipt = @{
    purchaseOrderId = $batchOrder.id
    warehouseId = $warehouse.id
    remark = "批次物资验收"
    items = @(@{ orderItemId = $batchItem.id; quantity = "2"; batchNo = $null; serialNumbers = @() })
}
Expect-Status "Post" "/receipts" $tokens.warehouse 400 $batchReceipt @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
$batchReceipt.items[0].batchNo = "BATCH-$([guid]::NewGuid().ToString('N').Substring(0, 10))"
$batchResult = Send-Data "Post" "/receipts" $tokens.warehouse $batchReceipt @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
Assert-True ($batchResult.orderStatus -eq "RECEIVED") "批次物资合法收货失败"

# 序列号必须逐个登记、数量匹配且全局唯一；失败请求应整体回滚后允许修正重试。
$serialOrder = New-Approved-Order $serialMaterial 2 5000 "序列号跟踪验收"
$serialItem = @($serialOrder.items) | Select-Object -First 1
$serialOne = "SN-$([guid]::NewGuid().ToString('N'))"
$serialReceipt = @{
    purchaseOrderId = $serialOrder.id
    warehouseId = $warehouse.id
    remark = "序列号第一次收货"
    items = @(@{ orderItemId = $serialItem.id; quantity = "1"; batchNo = $null; serialNumbers = @($serialOne) })
}
$serialFirst = Send-Data "Post" "/receipts" $tokens.warehouse $serialReceipt @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
Assert-True ($serialFirst.orderStatus -eq "PARTIALLY_RECEIVED") "序列号第一次收货状态错误"
$serialReceipt.remark = "重复序列号应被拒绝"
Expect-Status "Post" "/receipts" $tokens.warehouse 409 $serialReceipt @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
$serialReceipt.items[0].serialNumbers = @("SN-$([guid]::NewGuid().ToString('N'))")
$serialSecond = Send-Data "Post" "/receipts" $tokens.warehouse $serialReceipt @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
Assert-True ($serialSecond.orderStatus -eq "RECEIVED") "重复序列号回滚后修正收货失败"

[pscustomobject]@{
    result = "PASS"
    requisitionApprovalBranches = @("LOW", "MIDDLE", "HIGH")
    dataScopes = @("ALL", "DEPARTMENT", "SELF")
    resubmissionProcessInstances = $instanceCount
    purchaseOrderId = $orderId
    receiptCount = $receipts.Count
    finalOrderStatus = $closedOrder.status
    inventoryIncrease = "4"
    batchValidation = "PASS"
    serialValidation = "PASS"
} | ConvertTo-Json -Depth 5
