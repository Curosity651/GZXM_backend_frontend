$ErrorActionPreference = 'Stop'

Write-Host "Java:"
java -version
Write-Host "Node:"
node --version
Write-Host "npm:"
npm --version
Write-Host "Docker:"
docker version --format '{{.Server.Version}}'
Write-Host "Environment check complete. Java 21 is required for the backend."
