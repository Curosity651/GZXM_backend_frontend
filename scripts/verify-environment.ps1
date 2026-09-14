$ErrorActionPreference = 'Stop'

Write-Host "Java:"
java -version
Write-Host "Node:"
node --version
Write-Host "npm:"
npm --version
Write-Host "Docker:"
docker version --format '{{.Server.Version}}'
Write-Host "环境检查完成。后端要求 Java 21。"
