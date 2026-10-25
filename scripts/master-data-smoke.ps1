param([string]$BaseUrl = "http://localhost:3000")

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "主数据验收失败：$Message" }
}

function Login([string]$Username, [string]$Password) {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body $body).data
}

function Send([string]$Method, [string]$Path, [string]$Token, $Body = $null) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl/api/v1$Path"; Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 12
    }
    $response = Invoke-RestMethod @parameters
    Assert-True ($response.code -eq 0) "$Method $Path 返回业务错误"
    return $response.data
}

function Expect-Status([string]$Method, [string]$Path, [string]$Token, [int]$Status, $Body = $null) {
    try {
        Send $Method $Path $Token $Body | Out-Null
        throw "接口未按预期返回 HTTP $Status：$Method $Path"
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Status) { return }
        throw
    }
}

$masterData = Login "masterdata" "MasterData@123"
$token = $masterData.accessToken
$adminToken = (Login "admin" "Admin@123").accessToken
$viewerToken = (Login "viewer" "Viewer@123").accessToken
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$unitCode = "UT_$suffix"
$brandCode = "BR_$suffix"
$warehouseCode = "WH_$suffix"
$categoryCode = "CAT_$suffix"
$materialCode = "MAT_$suffix"
$unitId = $null
$brandId = $null
$warehouseId = $null
$categoryId = $null
$materialId = $null

$attributeDefinitions = @(
    @{ code="voltage"; name="额定电压"; valueType="NUMBER"; required=$true; options=$null; sortOrder=1 },
    @{ code="certification"; name="认证标准"; valueType="ENUM"; required=$true; options=@("CE","CCC"); sortOrder=2 }
)

try {
    Assert-True ("MASTER_DATA_ADMIN" -in $masterData.user.roles) "主数据管理员账号未分配专用角色"

    $warehouses = @(Send "Get" "/warehouses" $token)
    $units = @(Send "Get" "/units" $token)
    $brands = @(Send "Get" "/brands" $token)
    $categories = @(Send "Get" "/material-categories" $token)
    $materials = @(Send "Get" "/materials?keyword=TOOL-TORQUE-001" $token)
    Assert-True (($warehouses | Where-Object code -eq "QA_WH").Count -eq 1) "缺少质量检验仓测试数据"
    Assert-True (($units | Where-Object code -eq "KG").Count -eq 1) "缺少千克计量单位测试数据"
    Assert-True (($brands | Where-Object code -eq "OMRON").Count -eq 1) "缺少欧姆龙品牌测试数据"
    Assert-True (($categories | Where-Object code -eq "TOOLING").Count -eq 1) "缺少工装量具分类测试数据"
    Assert-True (($materials | Where-Object code -eq "TOOL-TORQUE-001").Count -eq 1) "缺少数显扭矩扳手测试物资"

    $unitId = (Send "Post" "/units" $token @{ code=$unitCode; name="验收测试单位"; precisionScale=2 }).id
    $brandId = (Send "Post" "/brands" $token @{ code=$brandCode; name="验收测试品牌" }).id
    $warehouseId = (Send "Post" "/warehouses" $token @{ code=$warehouseCode; name="验收测试仓"; address="测试园区A栋"; manager="测试管理员" }).id
    $categoryId = (Send "Post" "/material-categories" $token @{
        parentId=$null; code=$categoryCode; name="验收设备分类"; description="用于第二批CRUD自动验收"; sortOrder=99; attributes=$attributeDefinitions
    }).id
    Assert-True ((Send "Get" "/warehouses/$warehouseId" $token).code -eq $warehouseCode) "仓库详情查询不正确"
    Assert-True ((Send "Get" "/units/$unitId" $token).code -eq $unitCode) "计量单位详情查询不正确"
    Assert-True ((Send "Get" "/brands/$brandId" $token).code -eq $brandCode) "品牌详情查询不正确"
    Assert-True ((Send "Get" "/material-categories/$categoryId" $token).code -eq $categoryCode) "分类详情查询不正确"

    Expect-Status "Post" "/materials" $token 400 @{
        code="$materialCode-BAD"; name="缺少动态属性的物资"; categoryId=$categoryId; unitId=$unitId; brandId=$brandId
        specification="错误样例"; trackingMode="SERIAL"; minStock="1.00"; attributes=@{ voltage=380 }
    }

    $materialId = (Send "Post" "/materials" $token @{
        code=$materialCode; name="验收伺服电机"; categoryId=$categoryId; unitId=$unitId; brandId=$brandId
        specification="380V / 2kW"; trackingMode="SERIAL"; minStock="2.00"; attributes=@{ voltage=380; certification="CE" }
    }).id

    $detail = Send "Get" "/materials/$materialId" $token
    Assert-True ($detail.code -eq $materialCode -and $detail.trackingMode -eq "SERIAL") "新增物资详情不正确"

    Send "Put" "/materials/$materialId" $token @{
        code=$materialCode; name="验收伺服电机（已编辑）"; categoryId=$categoryId; unitId=$unitId; brandId=$brandId
        specification="380V / 3kW"; trackingMode="SERIAL"; minStock="3.00"; attributes=@{ voltage=380; certification="CCC" }
    } | Out-Null
    Assert-True ((Send "Get" "/materials/$materialId" $token).name -eq "验收伺服电机（已编辑）") "物资编辑未生效"

    Expect-Status "Put" "/materials/$materialId" $token 409 @{
        code="$materialCode-CHANGED"; name="非法改码"; categoryId=$categoryId; unitId=$unitId; brandId=$brandId
        specification="非法改码"; trackingMode="SERIAL"; minStock="3.00"; attributes=@{ voltage=380; certification="CCC" }
    }
    Expect-Status "Put" "/material-categories/$categoryId" $token 409 @{
        parentId=$null; code=$categoryCode; name="验收设备分类"; description="删除已使用属性"; sortOrder=99
        attributes=@($attributeDefinitions[1])
    }
    $attributesWithNewRequired = @($attributeDefinitions) + @(@{ code="phase"; name="相数"; valueType="NUMBER"; required=$true; options=$null; sortOrder=3 })
    Expect-Status "Put" "/material-categories/$categoryId" $token 409 @{
        parentId=$null; code=$categoryCode; name="验收设备分类"; description="新增必填属性"; sortOrder=99; attributes=$attributesWithNewRequired
    }

    Expect-Status "Delete" "/units/$unitId" $token 409
    Expect-Status "Delete" "/brands/$brandId" $token 409
    Expect-Status "Delete" "/material-categories/$categoryId" $token 409

    Send "Put" "/units/$unitId" $token @{ code=$unitCode; name="验收测试单位（已编辑）"; precisionScale=3 } | Out-Null
    Send "Put" "/brands/$brandId" $token @{ code=$brandCode; name="验收测试品牌（已编辑）" } | Out-Null
    Send "Put" "/warehouses/$warehouseId" $token @{ code=$warehouseCode; name="验收测试仓（已编辑）"; address="测试园区B栋"; manager="新管理员" } | Out-Null
    Send "Put" "/material-categories/$categoryId" $token @{
        parentId=$null; code=$categoryCode; name="验收设备分类（已编辑）"; description="属性兼容更新"; sortOrder=98; attributes=$attributeDefinitions
    } | Out-Null

    Send "Patch" "/materials/$materialId/status" $token @{ enabled=$false } | Out-Null
    Assert-True (-not (Send "Get" "/materials/$materialId" $token).enabled) "物资停用未生效"
    Send "Patch" "/materials/$materialId/status" $token @{ enabled=$true } | Out-Null
    foreach ($target in @(
        @{ path="warehouses/$warehouseId" }, @{ path="units/$unitId" }, @{ path="brands/$brandId" }
    )) {
        Send "Patch" "/$($target.path)/status" $token @{ enabled=$false } | Out-Null
        Send "Patch" "/$($target.path)/status" $token @{ enabled=$true } | Out-Null
    }

    Expect-Status "Post" "/brands" $viewerToken 403 @{ code="NO_ACCESS_$suffix"; name="越权品牌" }

    Send "Delete" "/materials/$materialId" $token | Out-Null
    $materialId = $null
    Send "Delete" "/material-categories/$categoryId" $token | Out-Null
    $categoryId = $null
    Send "Delete" "/warehouses/$warehouseId" $token | Out-Null
    $warehouseId = $null
    Send "Delete" "/brands/$brandId" $token | Out-Null
    $brandId = $null
    Send "Delete" "/units/$unitId" $token | Out-Null
    $unitId = $null

    $audits = @(Send "Get" "/audits" $adminToken)
    Assert-True (($audits | Where-Object { $_.method -eq "PATCH" -and $_.path -like "/api/v1/materials/*/status" }).Count -gt 0) "主数据状态变更未写入审计日志"

    Write-Host "主数据 CRUD 验收通过：演示数据、动态属性、增删改查、启停、引用保护、权限和审计均正常。" -ForegroundColor Green
} finally {
    if ($null -ne $materialId) { try { Send "Delete" "/materials/$materialId" $token | Out-Null } catch {} }
    if ($null -ne $categoryId) { try { Send "Delete" "/material-categories/$categoryId" $token | Out-Null } catch {} }
    if ($null -ne $warehouseId) { try { Send "Delete" "/warehouses/$warehouseId" $token | Out-Null } catch {} }
    if ($null -ne $brandId) { try { Send "Delete" "/brands/$brandId" $token | Out-Null } catch {} }
    if ($null -ne $unitId) { try { Send "Delete" "/units/$unitId" $token | Out-Null } catch {} }
}
