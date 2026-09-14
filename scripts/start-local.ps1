$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw '请先将 .env.example 复制为 .env，并设置仅用于本机的密码。'
}

docker compose --env-file $envFile -f (Join-Path $repoRoot 'infrastructure/compose.yaml') up -d
Write-Host 'MySQL 与 Redis 已启动。'
Write-Host '后端：进入 backend 后执行 .\mvnw.cmd spring-boot:run'
Write-Host '前端：进入 frontend 后执行 npm run dev'
