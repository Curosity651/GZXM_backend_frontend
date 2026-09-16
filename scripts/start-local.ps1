$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Copy .env.example to .env and set local passwords first.'
}

docker compose --env-file $envFile -f (Join-Path $repoRoot 'infrastructure/compose.yaml') up -d
Write-Host 'MySQL and Redis are running.'
Write-Host 'Backend: run scripts\run-backend.ps1 in another PowerShell window.'
Write-Host 'Frontend: enter frontend and run npm run dev in another PowerShell window.'
