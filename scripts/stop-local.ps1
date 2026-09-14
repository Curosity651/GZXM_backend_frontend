$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'

docker compose --env-file $envFile -f (Join-Path $repoRoot 'infrastructure/compose.yaml') down
Write-Host '本地基础服务已停止，数据卷仍保留。'
