$ports = @(8080, 8082, 8083, 8084, 8085, 8761, 5173)
$connections = foreach ($port in $ports) {
  Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
}
$processIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique
if (-not $processIds) {
  Write-Host "No frontend or Spring Cloud service process found on configured ports."
  exit 0
}
foreach ($processId in $processIds) {
  $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
  if ($process) {
    Write-Host "Stopping PID=$($process.Id) Process=$($process.ProcessName) Path=$($process.Path)"
    Stop-Process -Id $process.Id -Force
  }
}
