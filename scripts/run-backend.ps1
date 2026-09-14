$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw '请先将 .env.example 复制为 .env，并设置仅用于本机的密码。'
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        if ($parts.Count -eq 2) { [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process') }
    }
}

$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch 'version "21') { throw "后端需要 Java 21，当前为：$javaVersion" }

Push-Location (Join-Path $repoRoot 'backend')
try { .\mvnw.cmd spring-boot:run }
finally { Pop-Location }
