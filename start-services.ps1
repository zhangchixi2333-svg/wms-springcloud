$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $root ".logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$services = @(
  @{ Name = "wms-discovery"; Port = 8761; Module = "wms-discovery" },
  @{ Name = "wms-system-service"; Port = 8082; Module = "wms-system-service" },
  @{ Name = "wms-masterdata-service"; Port = 8083; Module = "wms-masterdata-service" },
  @{ Name = "wms-business-service"; Port = 8084; Module = "wms-business-service" },
  @{ Name = "wms-agent-service"; Port = 8085; Module = "wms-agent-service" },
  @{ Name = "wms-gateway"; Port = 8080; Module = "wms-gateway" }
)

mvn.cmd -q -DskipTests install | Out-Null

foreach ($service in $services) {
  $existing = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue
  if ($existing) {
    Write-Host "$($service.Name) port $($service.Port) already listening, skip start."
    continue
  }
  $out = Join-Path $logDir "$($service.Name).out.log"
  $err = Join-Path $logDir "$($service.Name).err.log"
  Start-Process -FilePath "mvn.cmd" `
    -ArgumentList @("-pl", $service.Module, "spring-boot:run") `
    -WorkingDirectory $root `
    -RedirectStandardOutput $out `
    -RedirectStandardError $err `
    -PassThru `
    -WindowStyle Hidden | Out-Null
  Write-Host "Started $($service.Name) on port $($service.Port). Logs: $out"
  Start-Sleep -Seconds 3
}
