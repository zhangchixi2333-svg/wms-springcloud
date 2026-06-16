param(
  [string]$Namespace = "wms",
  [string]$Release = "wms",
  [string]$ImageRepository = "wms-local",
  [string]$Tag = "",
  [string]$MysqlImage = "mysql:8.0",
  [switch]$SkipBuild,
  [switch]$Reset
)

$ErrorActionPreference = "Stop"

function Write-Step {
  param([string]$Message)
  Write-Host ""
  Write-Host "==> $Message" -ForegroundColor Cyan
}

function Require-Command {
  param([string]$Name)
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $Name"
  }
}

function Invoke-SmokeRequest {
  param(
    [string]$Url,
    [string]$Method = "GET",
    [string]$Body = ""
  )

  try {
    if ($Body) {
      return Invoke-WebRequest $Url `
        -Method $Method `
        -ContentType "application/json" `
        -Headers @{ Origin = "http://127.0.0.1:30081" } `
        -Body $Body `
        -UseBasicParsing
    }

    return Invoke-WebRequest $Url -Method $Method -UseBasicParsing
  } catch {
    $status = $null
    $responseBody = ""
    if ($_.Exception.Response) {
      $status = $_.Exception.Response.StatusCode.value__
      $stream = $_.Exception.Response.GetResponseStream()
      if ($stream) {
        $reader = New-Object System.IO.StreamReader($stream)
        $responseBody = $reader.ReadToEnd()
      }
    }
    throw "Smoke request failed: $Method $Url status=$status body=$responseBody"
  }
}

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if (-not $Tag) {
  $Tag = "local-$(Get-Date -Format 'yyyyMMddHHmmss')"
}

Write-Step "Checking local tools"
Require-Command docker
Require-Command kubectl
Require-Command helm

$context = kubectl config current-context
Write-Host "kubectl context: $context"
if ($context -ne "docker-desktop") {
  Write-Warning "Current kubectl context is '$context'. This script is optimized for Docker Desktop Kubernetes."
  Write-Warning "For kind/minikube, load local Docker images into the cluster before deploying."
}

$services = @(
  "wms-discovery",
  "wms-gateway",
  "wms-system-service",
  "wms-masterdata-service",
  "wms-business-service"
)

if (-not $SkipBuild) {
  Write-Step "Building backend service images"
  foreach ($service in $services) {
    $image = "${ImageRepository}:$service-$Tag"
    Write-Host "Building $image"
    docker build `
      -f "$Root\deploy\docker\Dockerfile.service" `
      --build-arg MODULE=$service `
      -t $image `
      $Root
  }

  Write-Step "Building frontend image"
  $frontendImage = "${ImageRepository}:wms-frontend-$Tag"
  Write-Host "Building $frontendImage"
  docker build `
    -f "$Root\deploy\docker\Dockerfile.frontend" `
    --build-arg VITE_API_BASE=/api `
    -t $frontendImage `
    $Root
} else {
  Write-Step "Skipping image build"
}

if ($Reset) {
  Write-Step "Resetting previous local release and MySQL PVC"
  helm uninstall $Release -n $Namespace --ignore-not-found | Out-Host
  kubectl delete pvc -n $Namespace -l app=mysql --ignore-not-found | Out-Host
}

Write-Step "Deploying Helm release"
helm upgrade --install $Release "$Root\deploy\helm\wms" `
  -n $Namespace `
  --create-namespace `
  --timeout 10m `
  --set "image.repository=$ImageRepository" `
  --set "image.tag=$Tag" `
  --set "image.pullPolicy=IfNotPresent" `
  --set "mysql.image=$MysqlImage"

Write-Step "Waiting for MySQL"
kubectl rollout status statefulset/mysql -n $Namespace --timeout=300s
kubectl wait --for=condition=Ready pod/mysql-0 -n $Namespace --timeout=300s

Write-Step "Waiting for database initialization job"
kubectl wait --for=condition=Complete job/wms-mysql-init -n $Namespace --timeout=300s

Write-Step "Waiting for WMS deployments"
$deployments = @(
  "wms-discovery",
  "wms-gateway",
  "wms-system-service",
  "wms-masterdata-service",
  "wms-business-service",
  "wms-frontend"
)

foreach ($deployment in $deployments) {
  kubectl rollout status "deployment/$deployment" -n $Namespace --timeout=300s
}

Write-Step "Checking resource status"
kubectl get pods -n $Namespace -o wide
kubectl get svc -n $Namespace -o wide

Write-Step "Running smoke tests"
$frontend = Invoke-SmokeRequest "http://127.0.0.1:30081/"
Write-Host "frontend / => HTTP $($frontend.StatusCode)"

$loginBody = @{
  username = "admin"
  password = "admin123"
} | ConvertTo-Json

$login = Invoke-SmokeRequest "http://127.0.0.1:30081/api/auth/login" "POST" $loginBody
Write-Host "frontend /api/auth/login => HTTP $($login.StatusCode)"

kubectl exec -n $Namespace mysql-0 -- mysql -uwms -pwms123456 --default-character-set=utf8mb4 wms_cloud -e "SELECT COUNT(*) AS app_user_count FROM app_user;"

Write-Host ""
Write-Host "Local WMS deployment completed." -ForegroundColor Green
Write-Host "Frontend: http://127.0.0.1:30081"
Write-Host "Gateway:  http://127.0.0.1:30080/api"
Write-Host "Image tag: $Tag"
Write-Host ""
Write-Host "Useful commands:"
Write-Host "  kubectl get pods -n $Namespace"
Write-Host "  kubectl logs -n $Namespace deploy/wms-gateway --tail=120"
Write-Host "  helm status $Release -n $Namespace"
