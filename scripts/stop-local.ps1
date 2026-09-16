$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'

docker compose --env-file $envFile -f (Join-Path $repoRoot 'infrastructure/compose.yaml') down
Write-Host 'Local services stopped. Docker volumes were preserved.'
