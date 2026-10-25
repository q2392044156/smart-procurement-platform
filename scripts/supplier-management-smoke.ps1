param([string]$BaseUrl = "http://localhost:3000")

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "供应商管理验收失败：$Message" }
}

function Login([string]$Username, [string]$Password) {
    $body = @{ username=$Username; password=$Password } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body $body).data
}

function Send([string]$Method, [string]$Path, [string]$Token, $Body = $null) {
    $parameters = @{ Method=$Method; Uri="$BaseUrl/api/v1$Path"; Headers=@{ Authorization="Bearer $Token" } }
    if ($null -ne $Body) { $parameters.ContentType="application/json"; $parameters.Body=$Body | ConvertTo-Json -Depth 12 }
    $response = Invoke-RestMethod @parameters
    Assert-True ($response.code -eq 0) "$Method $Path 返回业务错误"
    return $response.data
}

function Expect-Status([string]$Method, [string]$Path, [string]$Token, [int]$Status, $Body = $null) {
    try { Send $Method $Path $Token $Body | Out-Null; throw "接口未按预期返回 HTTP $Status：$Method $Path" }
    catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Status) { return }; throw }
}

$supplierAdmin = Login "supplieradmin" "Supplier@123"
$token = $supplierAdmin.accessToken
$adminToken = (Login "admin" "Admin@123").accessToken
$viewerToken = (Login "viewer" "Viewer@123").accessToken
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$supplierCode = "SUP-TST-$suffix"
$supplierId = $null
$primaryContactId = $null
$secondaryContactId = $null
$qualificationId = $null
$evaluationId = $null
$fileId = $null

try {
    Assert-True ("SUPPLIER_ADMIN" -in $supplierAdmin.user.roles) "供应商管理员账号未分配专用角色"
    $fixed = @(Send "Get" "/suppliers?keyword=SUP-AUTO-001" $token) | Select-Object -First 1
    Assert-True ($null -ne $fixed -and $fixed.contactCount -ge 2 -and $fixed.qualificationCount -ge 2 -and $fixed.evaluationCount -ge 2) "第三批固定演示供应商数据不完整"
    $fixedDetail = Send "Get" "/suppliers/$($fixed.id)" $token
    Assert-True (($fixedDetail.contacts | Where-Object primaryContact).Count -eq 1) "固定演示供应商主要联系人不唯一"

    $supplierId = (Send "Post" "/suppliers" $token @{
        code=$supplierCode; name="第三批验收自动化设备供应商"; unifiedCreditCode="TST-$suffix"; level="B"; status="PENDING"
        contactName="初始联系人"; contactPhone="13800138000"; email="initial.$suffix@example.com"; address="验收工业园"
    }).id
    $detail = Send "Get" "/suppliers/$supplierId" $token
    Assert-True ($detail.status -eq "PENDING" -and $detail.contacts.Count -eq 1) "供应商及初始联系人创建不正确"
    $primaryContactId = $detail.contacts[0].id

    Expect-Status "Patch" "/suppliers/$supplierId/status" $token 409 @{ status="QUALIFIED" }
    Expect-Status "Put" "/suppliers/$supplierId" $token 409 @{
        code="$supplierCode-CHANGED"; name="非法改码"; unifiedCreditCode="TST-$suffix"; level="B"; status="PENDING"
        contactName="初始联系人"; contactPhone="13800138000"; email="initial.$suffix@example.com"; address="验收工业园"
    }

    $secondaryContactId = (Send "Post" "/suppliers/$supplierId/contacts" $token @{
        name="技术接口人"; position="技术经理"; phone="13800138001"; email="tech.$suffix@example.com"; primaryContact=$false
    }).id
    Send "Put" "/suppliers/$supplierId/contacts/$secondaryContactId" $token @{
        name="技术接口人（主要）"; position="技术总监"; phone="13800138002"; email="tech.$suffix@example.com"; primaryContact=$true
    } | Out-Null
    $detail = Send "Get" "/suppliers/$supplierId" $token
    Assert-True ($detail.contactName -eq "技术接口人（主要）" -and ($detail.contacts | Where-Object primaryContact).Count -eq 1) "主要联系人切换或供应商摘要同步失败"
    Send "Delete" "/suppliers/$supplierId/contacts/$primaryContactId" $token | Out-Null
    $primaryContactId = $null

    $presign = Send "Post" "/files/presign" $token @{ fileName="supplier-certificate-$suffix.pdf"; contentType="application/pdf"; size=24 }
    $fileId = $presign.fileId
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("%PDF-1.4 supplier test")
    $uploadResponse = Invoke-WebRequest -Method Put -Uri $presign.uploadUrl -ContentType "application/pdf" -Headers @{ Origin="http://localhost:3000" } -Body $bytes
    Assert-True ($null -ne $uploadResponse.Headers["Access-Control-Allow-Origin"]) "MinIO 未返回浏览器跨域上传许可"

    Expect-Status "Post" "/suppliers/$supplierId/qualifications" $token 400 @{
        name="日期错误资质"; fileId=$null; validFrom="2028-01-01"; validUntil="2027-01-01"
    }
    $qualificationId = (Send "Post" "/suppliers/$supplierId/qualifications" $token @{
        name="ISO 9001质量体系认证"; fileId=$fileId; validFrom="2026-01-01"; validUntil="2029-12-31"
    }).id
    Expect-Status "Post" "/suppliers/$supplierId/qualifications" $token 409 @{
        name="ISO 9001质量体系认证"; fileId=$null; validFrom="2026-01-01"; validUntil="2029-12-31"
    }
    $download = Send "Get" "/files/$fileId" $token
    Assert-True ($download.fileName -eq "supplier-certificate-$suffix.pdf") "资质附件未正确关联或不可下载"
    Expect-Status "Delete" "/files/$fileId" $token 409

    Send "Patch" "/suppliers/$supplierId/status" $token @{ status="QUALIFIED" } | Out-Null
    Assert-True ((Send "Get" "/suppliers/$supplierId" $token).status -eq "QUALIFIED") "有效资质录入后未能设为合格供应商"
    Expect-Status "Delete" "/suppliers/$supplierId/qualifications/$qualificationId" $token 409

    $evaluationId = (Send "Post" "/suppliers/$supplierId/evaluations" $token @{
        period="2026-Q4"; qualityScore="90"; deliveryScore="80"; serviceScore="100"; comment="第三批验收初评"
    }).id
    $detail = Send "Get" "/suppliers/$supplierId" $token
    Assert-True ([decimal]$detail.score -eq 90 -and [decimal]$detail.evaluations[0].totalScore -eq 90) "绩效总分或供应商评分计算错误"
    Expect-Status "Post" "/suppliers/$supplierId/evaluations" $token 409 @{
        period="2026-Q4"; qualityScore=90; deliveryScore=90; serviceScore=90; comment="重复期间"
    }
    Expect-Status "Post" "/suppliers/$supplierId/evaluations" $token 400 @{
        period="2026-Q3"; qualityScore=101; deliveryScore=90; serviceScore=90; comment="非法评分"
    }
    Send "Put" "/suppliers/$supplierId/evaluations/$evaluationId" $token @{
        period="2026-Q4"; qualityScore=96; deliveryScore=96; serviceScore=96; comment="第三批验收复评"
    } | Out-Null
    $detail = Send "Get" "/suppliers/$supplierId" $token
    Assert-True ([decimal]$detail.score -eq 96) "绩效编辑后供应商评分未回算"

    $referenced = @(Send "Get" "/suppliers" $token) | Where-Object { [int64]$_.orderCount -gt 0 } | Select-Object -First 1
    if ($null -ne $referenced) { Expect-Status "Delete" "/suppliers/$($referenced.id)" $token 409 }
    Expect-Status "Post" "/suppliers" $viewerToken 403 @{
        code="SUP-NOACCESS-$suffix"; name="越权供应商"; unifiedCreditCode=""; level="B"; status="PENDING"; contactName=""; contactPhone=""; email=""; address=""
    }

    Send "Delete" "/suppliers/$supplierId/evaluations/$evaluationId" $token | Out-Null
    $evaluationId = $null
    $detail = Send "Get" "/suppliers/$supplierId" $token
    Assert-True ([decimal]$detail.score -eq 0) "删除最后一条绩效后评分未归零"
    Send "Patch" "/suppliers/$supplierId/status" $token @{ status="SUSPENDED" } | Out-Null
    Send "Delete" "/suppliers/$supplierId/qualifications/$qualificationId" $token | Out-Null
    $qualificationId = $null
    Send "Delete" "/files/$fileId" $token | Out-Null
    $fileId = $null
    Send "Delete" "/suppliers/$supplierId/contacts/$secondaryContactId" $token | Out-Null
    $secondaryContactId = $null
    Send "Delete" "/suppliers/$supplierId" $token | Out-Null
    $supplierId = $null

    $audits = @(Send "Get" "/audits" $adminToken)
    Assert-True (($audits | Where-Object { $_.method -eq "PATCH" -and $_.path -like "/api/v1/suppliers/*/status" }).Count -gt 0) "供应商状态变更未写入操作审计"
    Write-Host "供应商全生命周期验收通过：档案、联系人、资质附件、准入状态、绩效回算、删除保护、权限和审计均正常。" -ForegroundColor Green
} finally {
    if ($null -ne $supplierId) {
        try { Send "Patch" "/suppliers/$supplierId/status" $token @{ status="SUSPENDED" } | Out-Null } catch {}
        if ($null -ne $evaluationId) { try { Send "Delete" "/suppliers/$supplierId/evaluations/$evaluationId" $token | Out-Null } catch {} }
        if ($null -ne $qualificationId) { try { Send "Delete" "/suppliers/$supplierId/qualifications/$qualificationId" $token | Out-Null } catch {} }
        try { Send "Delete" "/suppliers/$supplierId" $token | Out-Null } catch {}
    }
    if ($null -ne $fileId) { try { Send "Delete" "/files/$fileId" $token | Out-Null } catch {} }
}
