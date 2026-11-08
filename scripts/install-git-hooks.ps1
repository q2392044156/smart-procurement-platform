$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

git config user.name "WangQF"
git config user.email "2392044156@qq.com"
git config core.hooksPath "scripts/git-hooks"

Write-Host "Git identity : WangQF <2392044156@qq.com>"
Write-Host "Hooks path   : scripts/git-hooks (pre-commit verifies author and committer)"
Write-Host "Current      : $(git var GIT_AUTHOR_IDENT)"
