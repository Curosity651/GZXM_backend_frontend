$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Copy .env.example to .env and set local passwords first.'
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        if ($parts.Count -eq 2) { [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process') }
    }
}

$javaCandidates = New-Object System.Collections.Generic.List[string]

if ($env:JAVA_HOME) {
    $javaCandidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
}

$pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
if ($pathJava) {
    $javaCandidates.Add($pathJava.Source)
}

$jdkSearchRoots = @(
    'C:\tools',
    'C:\Program Files\Java',
    'C:\Program Files\Eclipse Adoptium',
    (Join-Path $env:USERPROFILE '.jdks')
)

foreach ($searchRoot in $jdkSearchRoots) {
    if (Test-Path -LiteralPath $searchRoot) {
        Get-ChildItem -LiteralPath $searchRoot -Directory -Filter '*21*' -ErrorAction SilentlyContinue | ForEach-Object {
            $javaCandidates.Add((Join-Path $_.FullName 'bin\java.exe'))
        }
    }
}

$javaExe = $null
$javaVersion = $null
foreach ($candidate in ($javaCandidates | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath $candidate)) { continue }
    $candidateVersion = (& cmd.exe /d /c ('"{0}" -version 2>&1' -f $candidate) | Select-Object -First 1)
    if ($candidateVersion -match 'version "21(?:\.|"|\s)') {
        $javaExe = $candidate
        $javaVersion = $candidateVersion
        break
    }
}

if (-not $javaExe) {
    throw 'Java 21 was not found. Install JDK 21 or set JAVA_HOME to a JDK 21 directory.'
}

$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
Write-Host "Using $javaVersion from $env:JAVA_HOME"

Push-Location (Join-Path $repoRoot 'backend')
try { .\mvnw.cmd spring-boot:run }
finally { Pop-Location }
