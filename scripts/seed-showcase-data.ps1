param(
    [string]$BaseUrl = "http://localhost:3000",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Marker = "[业务展示数据-v1]"

function Login([string]$Username, [string]$Password) {
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)
    if ($response.code -ne 0) { throw "$Username 登录失败：$($response.message)" }
    return $response.data.accessToken
}

function Headers([string]$Token) { return @{ Authorization = "Bearer $Token" } }

function Get-Data([string]$Path, [string]$Token) {
    $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1$Path" -Headers (Headers $Token)
    if ($response.code -ne 0) { throw "GET $Path 失败：$($response.message)" }
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
    if ($response.code -ne 0) { throw "$Method $Path 失败：$($response.message)" }
    return $response.data
}

function Task-For([string]$Token, [string]$BusinessKey) {
    return @(Get-Data "/workflow/tasks" $Token | Where-Object { $_.businessKey -eq $BusinessKey }) | Select-Object -First 1
}

function Approve-For([string]$Token, [string]$BusinessKey, [string]$Comment) {
    $task = Task-For $Token $BusinessKey
    if ($null -eq $task) { throw "未找到待办：$BusinessKey" }
    Send-Data "Post" "/workflow/$($task.id)/approve" $Token @{ comment = $Comment } | Out-Null
}

function Reject-For([string]$Token, [string]$BusinessKey, [string]$Comment) {
    $task = Task-For $Token $BusinessKey
    if ($null -eq $task) { throw "未找到待办：$BusinessKey" }
    Send-Data "Post" "/workflow/$($task.id)/reject" $Token @{ comment = $Comment } | Out-Null
}

function New-Requisition($Material, [decimal]$Quantity, [decimal]$UnitPrice, [string]$Scenario) {
    $payload = @{
        requiredDate = (Get-Date).AddDays(20).ToString("yyyy-MM-dd")
        purpose = "$Marker $Scenario"
        items = @(@{
            materialId = $Material.id
            quantity = "$Quantity"
            estimatedUnitPrice = "$UnitPrice"
            remark = "简历演示数据：$Scenario"
        })
    }
    return (Send-Data "Post" "/requisitions" $Tokens.requester $payload).id
}

function Submit-Requisition([string]$Id) {
    Send-Data "Post" "/requisitions/$Id/submit" $Tokens.requester | Out-Null
    return "REQUISITION:$Id"
}

function Approve-Requisition([string]$Id, [ValidateSet("LOW", "MIDDLE", "HIGH")] [string]$Level) {
    $key = Submit-Requisition $Id
    Approve-For $Tokens.manager $key "展示数据：部门预算与用途审核通过"
    Approve-For $Tokens.purchaser $key "展示数据：采购可执行性审核通过"
    if ($Level -in @("MIDDLE", "HIGH")) { Approve-For $Tokens.finance $key "展示数据：财务预算审核通过" }
    if ($Level -eq "HIGH") { Approve-For $Tokens.gm $key "展示数据：重大采购决策通过" }
}

function Convert-To-Order([string]$RequisitionId, [string]$Terms) {
    $payload = @{
        supplierId = $QualifiedSupplier.id
        deliveryDate = (Get-Date).AddDays(35).ToString("yyyy-MM-dd")
        taxRate = "0.13"
        terms = "$Marker $Terms"
        priceOverrides = @{}
    }
    return (Send-Data "Post" "/requisitions/$RequisitionId/convert" $Tokens.purchaser $payload).id
}

function New-Order($Material, [decimal]$Quantity, [decimal]$UnitPrice, [string]$Scenario, [ValidateSet("LOW", "HIGH")] [string]$Level = "LOW") {
    $requestId = New-Requisition $Material $Quantity $UnitPrice "订单来源：$Scenario"
    Approve-Requisition $requestId $Level
    $orderId = Convert-To-Order $requestId $Scenario
    return [pscustomobject]@{ requisitionId = $requestId; orderId = $orderId }
}

function Submit-Order([string]$OrderId) {
    Send-Data "Post" "/purchase-orders/$OrderId/submit" $Tokens.purchaser | Out-Null
    return "PURCHASE_ORDER:$OrderId"
}

function Approve-Order([string]$OrderId, [switch]$HighValue) {
    $key = Submit-Order $OrderId
    Approve-For $Tokens.finance $key "展示数据：商业条款复核通过"
    if ($HighValue) { Approve-For $Tokens.gm $key "展示数据：高金额订单审批通过" }
}

function Receive-Order([string]$OrderId, [decimal]$Quantity, [string]$Remark, [string]$BatchNo = $null, [string[]]$SerialNumbers = @()) {
    $order = Get-Data "/purchase-orders/$OrderId" $Tokens.purchaser
    $item = @($order.items) | Select-Object -First 1
    $payload = @{
        purchaseOrderId = $OrderId
        warehouseId = $Warehouse.id
        remark = "$Marker $Remark"
        items = @(@{
            orderItemId = $item.id
            quantity = "$Quantity"
            batchNo = $BatchNo
            serialNumbers = $SerialNumbers
        })
    }
    return Send-Data "Post" "/receipts" $Tokens.warehouse $payload @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
}

$Tokens = @{
    admin = Login "admin" "Admin@123"
    requester = Login "requester" "Request@123"
    manager = Login "manager" "Manager@123"
    purchaser = Login "purchaser" "Purchase@123"
    finance = Login "finance" "Finance@123"
    warehouse = Login "warehouse" "Warehouse@123"
    gm = Login "gm" "Gm@123"
}

$existing = @(Get-Data "/requisitions" $Tokens.requester)
if (-not $Force -and @($existing | Where-Object { $_.purpose -eq "$Marker 初始化完成标记" }).Count -gt 0) {
    [pscustomobject]@{
        result = "SKIPPED"
        message = "展示数据已经存在；如需再次生成，请使用 -Force。"
        marker = $Marker
        existingScenarioCount = @($existing | Where-Object { $_.purpose.StartsWith($Marker) }).Count
    } | ConvertTo-Json -Depth 5
    exit 0
}

$materials = @(Get-Data "/materials" $Tokens.requester)
$QuantityMaterial = $materials | Where-Object { $_.trackingMode -eq "QUANTITY" } | Select-Object -First 1
$BatchMaterial = $materials | Where-Object { $_.trackingMode -eq "BATCH" } | Select-Object -First 1
$SerialMaterial = $materials | Where-Object { $_.trackingMode -eq "SERIAL" } | Select-Object -First 1
$Warehouse = @(Get-Data "/warehouses" $Tokens.warehouse) | Select-Object -First 1
$QualifiedSupplier = @(Get-Data "/suppliers" $Tokens.purchaser | Where-Object { $_.status -eq "QUALIFIED" }) | Select-Object -First 1
if ($null -eq $QuantityMaterial -or $null -eq $BatchMaterial -or $null -eq $SerialMaterial -or $null -eq $Warehouse -or $null -eq $QualifiedSupplier) {
    throw "基础演示物资、仓库或合格供应商不完整"
}

# 补充供应商生命周期状态，用于列表筛选和状态展示。
$supplierScenarios = @(
    @{ code = "SHOW-PENDING"; name = "待准入电子设备供应商"; level = "B"; status = "PENDING" },
    @{ code = "SHOW-SUSPEND"; name = "暂停合作工业备件供应商"; level = "C"; status = "SUSPENDED" },
    @{ code = "SHOW-BLACK"; name = "黑名单历史供应商"; level = "D"; status = "BLACKLISTED" }
)
$supplierRows = @(Get-Data "/suppliers" $Tokens.purchaser)
foreach ($supplier in $supplierScenarios) {
    if (-not ($supplierRows | Where-Object { $_.code -eq $supplier.code })) {
        Send-Data "Post" "/suppliers" $Tokens.purchaser @{
            code = $supplier.code; name = $supplier.name; unifiedCreditCode = "SHOWCASE-$($supplier.code)"
            level = $supplier.level; status = $supplier.status; contactName = "演示联系人"
            contactPhone = "13800009999"; email = "showcase@example.com"; address = "制造业供应链演示地址"
        } | Out-Null
    }
}

$ScenarioIds = [ordered]@{}

# 采购申请生命周期与三档审批链。
$ScenarioIds.requisitionDraft = New-Requisition $QuantityMaterial 3 800 "申请-草稿待提交"

$ScenarioIds.requisitionPendingDepartment = New-Requisition $BatchMaterial 20 120 "申请-等待部门经理审批"
Submit-Requisition $ScenarioIds.requisitionPendingDepartment | Out-Null

$ScenarioIds.requisitionPendingPurchaser = New-Requisition $QuantityMaterial 10 600 "申请-等待采购经理审批"
$key = Submit-Requisition $ScenarioIds.requisitionPendingPurchaser
Approve-For $Tokens.manager $key "展示数据：部门经理已通过"

$ScenarioIds.requisitionPendingFinance = New-Requisition $SerialMaterial 1 80000 "申请-中金额等待财务审批"
$key = Submit-Requisition $ScenarioIds.requisitionPendingFinance
Approve-For $Tokens.manager $key "展示数据：部门经理已通过"
Approve-For $Tokens.purchaser $key "展示数据：采购经理已通过"

$ScenarioIds.requisitionPendingGm = New-Requisition $SerialMaterial 1 650000 "申请-重大采购等待总经理审批"
$key = Submit-Requisition $ScenarioIds.requisitionPendingGm
Approve-For $Tokens.manager $key "展示数据：部门经理已通过"
Approve-For $Tokens.purchaser $key "展示数据：采购经理已通过"
Approve-For $Tokens.finance $key "展示数据：财务已通过"

$ScenarioIds.requisitionRejected = New-Requisition $QuantityMaterial 5 500 "申请-已驳回待修改"
$key = Submit-Requisition $ScenarioIds.requisitionRejected
Reject-For $Tokens.manager $key "展示数据：用途说明不足，请修改后重新提交"

$ScenarioIds.requisitionApproved = New-Requisition $QuantityMaterial 8 900 "申请-审批完成待转订单"
Approve-Requisition $ScenarioIds.requisitionApproved "LOW"

# 采购订单生命周期。
$draftOrder = New-Order $QuantityMaterial 5 1100 "订单-草稿待提交"
$ScenarioIds.orderDraft = $draftOrder.orderId

$pendingFinanceOrder = New-Order $BatchMaterial 12 500 "订单-等待财务复核"
Submit-Order $pendingFinanceOrder.orderId | Out-Null
$ScenarioIds.orderPendingFinance = $pendingFinanceOrder.orderId

$pendingGmOrder = New-Order $SerialMaterial 1 650000 "订单-高金额等待总经理审批" "HIGH"
$key = Submit-Order $pendingGmOrder.orderId
Approve-For $Tokens.finance $key "展示数据：财务商业条款已通过"
$ScenarioIds.orderPendingGm = $pendingGmOrder.orderId

$approvedOrder = New-Order $QuantityMaterial 7 1300 "订单-已审批待收货"
Approve-Order $approvedOrder.orderId
$ScenarioIds.orderApproved = $approvedOrder.orderId

$partialOrder = New-Order $QuantityMaterial 6 1500 "订单-部分收货"
Approve-Order $partialOrder.orderId
Receive-Order $partialOrder.orderId 2 "第一次到货，保留剩余待收" | Out-Null
$ScenarioIds.orderPartiallyReceived = $partialOrder.orderId

$batchOrder = New-Order $BatchMaterial 10 260 "订单-批次物资全部收货"
Approve-Order $batchOrder.orderId
Receive-Order $batchOrder.orderId 10 "批次物资整批到货" "SHOWCASE-BATCH-001" | Out-Null
$ScenarioIds.orderReceivedBatch = $batchOrder.orderId

$serialOrder = New-Order $SerialMaterial 2 12000 "订单-序列号物资全部收货"
Approve-Order $serialOrder.orderId
$serials = @("SHOWCASE-SN-$([guid]::NewGuid().ToString('N').Substring(0,12))", "SHOWCASE-SN-$([guid]::NewGuid().ToString('N').Substring(0,12))")
Receive-Order $serialOrder.orderId 2 "序列号设备逐台登记" $null $serials | Out-Null
$ScenarioIds.orderReceivedSerial = $serialOrder.orderId

# 只有全部场景成功后才写入完成标记，保证默认重复执行安全。
$ScenarioIds.completionMarker = New-Requisition $QuantityMaterial 1 1 "初始化完成标记"

[pscustomobject]@{
    result = "CREATED"
    marker = $Marker
    supplierStates = @("PENDING", "SUSPENDED", "BLACKLISTED")
    requisitionStates = @("DRAFT", "PENDING_DEPARTMENT", "PENDING_PURCHASER", "PENDING_FINANCE", "PENDING_GENERAL_MANAGER", "REJECTED", "APPROVED", "CONVERTED")
    orderStates = @("DRAFT", "PENDING_FINANCE", "PENDING_GENERAL_MANAGER", "APPROVED", "PARTIALLY_RECEIVED", "RECEIVED_BATCH", "RECEIVED_SERIAL")
    inventoryModes = @("QUANTITY", "BATCH", "SERIAL")
    ids = $ScenarioIds
} | ConvertTo-Json -Depth 8
