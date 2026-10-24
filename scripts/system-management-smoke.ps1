param([string]$BaseUrl = "http://localhost:3000")

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "系统管理验收失败：$Message" }
}

function Login([string]$Username, [string]$Password) {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body $body).data
}

function Headers([string]$Token) { return @{ Authorization = "Bearer $Token" } }

function Send([string]$Method, [string]$Path, [string]$Token, $Body = $null) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl/api/v1$Path"; Headers = (Headers $Token) }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 10
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

$admin = Login "admin" "Admin@123"
$token = $admin.accessToken
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$departmentCode = "TST_$suffix"
$roleCode = "CRUD_TEST_$suffix"
$username = "crud.user.$suffix"
$departmentId = $null
$roleId = $null
$userId = $null

try {
    $departments = @(Send "Get" "/departments" $token)
    $headquarters = $departments | Where-Object { $_.code -eq "HQ" } | Select-Object -First 1
    Assert-True ($null -ne $headquarters) "缺少集团总部演示部门"

    $permissions = @(Send "Get" "/permissions" $token)
    $roles = @(Send "Get" "/roles" $token)
    $dashboardPermission = $permissions | Where-Object { $_.code -eq "dashboard:view" } | Select-Object -First 1
    $masterEditPermission = $permissions | Where-Object { $_.code -eq "masterdata:edit" } | Select-Object -First 1
    $applicantRole = $roles | Where-Object { $_.code -eq "APPLICANT" } | Select-Object -First 1
    Assert-True ($null -ne $dashboardPermission -and $null -ne $masterEditPermission) "缺少测试所需权限"
    Assert-True ($null -ne $applicantRole) "缺少申请人内置角色"

    $departmentId = (Send "Post" "/departments" $token @{ parentId=$headquarters.id; code=$departmentCode; name="CRUD验收部门"; sortOrder=99 }).id
    Send "Put" "/departments/$departmentId" $token @{ parentId=$headquarters.id; code=$departmentCode; name="CRUD验收部门（已编辑）"; sortOrder=98 } | Out-Null

    $roleId = (Send "Post" "/roles" $token @{ code=$roleCode; name="CRUD验收角色"; dataScope="SELF"; permissionIds=@($dashboardPermission.id) }).id
    $userId = (Send "Post" "/users" $token @{ username=$username; password="Crud@Test123"; displayName="CRUD验收用户"; departmentId=$departmentId; roleIds=@($roleId) }).id

    $testUser = Login $username "Crud@Test123"
    Assert-True ($testUser.user.departmentId -eq $departmentId) "新增用户部门不正确"
    Assert-True ($roleCode -in $testUser.user.roles) "新增用户角色不正确"
    Expect-Status "Get" "/users" $testUser.accessToken 403

    Send "Put" "/roles/$roleId" $token @{ code=$roleCode; name="CRUD验收角色（已编辑）"; dataScope="DEPARTMENT"; permissionIds=@($dashboardPermission.id,$masterEditPermission.id) } | Out-Null
    $materials = @(Send "Get" "/materials" $testUser.accessToken)
    Assert-True ($materials.Count -gt 0) "角色权限修改未实时生效"

    Send "Put" "/users/$userId" $token @{ displayName="CRUD验收用户（已编辑）"; departmentId=$departmentId; roleIds=@($roleId,$applicantRole.id) } | Out-Null
    Send "Patch" "/users/$userId/status" $token @{ enabled=$false } | Out-Null
    Expect-Status "Post" "/auth/login" "" 401 @{ username=$username; password="Crud@Test123" }
    Send "Patch" "/users/$userId/status" $token @{ enabled=$true } | Out-Null
    Send "Post" "/users/$userId/reset-password" $token @{ password="Changed@Test123" } | Out-Null
    $changedUser = Login $username "Changed@Test123"
    Assert-True ($changedUser.user.displayName -eq "CRUD验收用户（已编辑）") "用户编辑或密码重置未生效"
    Assert-True ($changedUser.user.roles.Count -eq 2) "用户多角色分配未生效"

    Expect-Status "Delete" "/roles/$roleId" $token 409
    Expect-Status "Patch" "/users/$($admin.user.id)/status" $token 409 @{ enabled=$false }

    Send "Delete" "/users/$userId" $token | Out-Null
    $userId = $null
    Send "Delete" "/roles/$roleId" $token | Out-Null
    $roleId = $null
    Send "Delete" "/departments/$departmentId" $token | Out-Null
    $departmentId = $null

    $audits = @(Send "Get" "/audits" $token)
    Assert-True (($audits | Where-Object { $_.method -eq "PATCH" -and $_.path -like "/api/v1/users/*/status" }).Count -gt 0) "PATCH 操作未写入审计日志"

    Write-Host "系统管理 CRUD 验收通过：部门、角色、权限、用户、启停、密码重置、删除约束和审计均正常。" -ForegroundColor Green
} finally {
    if ($null -ne $userId) { try { Send "Delete" "/users/$userId" $token | Out-Null } catch {} }
    if ($null -ne $roleId) { try { Send "Delete" "/roles/$roleId" $token | Out-Null } catch {} }
    if ($null -ne $departmentId) { try { Send "Delete" "/departments/$departmentId" $token | Out-Null } catch {} }
}
