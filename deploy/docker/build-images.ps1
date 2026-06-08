$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\..")
$imagePrefix = $env:WMS_IMAGE_PREFIX
if (-not $imagePrefix) {
  $imagePrefix = "wms"
}
$tag = $env:WMS_IMAGE_TAG
if (-not $tag) {
  $tag = "0.0.1"
}

$services = @(
  "wms-discovery",
  "wms-gateway",
  "wms-system-service",
  "wms-masterdata-service",
  "wms-business-service"
)

foreach ($service in $services) {
  docker build `
    -f "$root\deploy\docker\Dockerfile.service" `
    --build-arg MODULE=$service `
    -t "$imagePrefix/$service`:$tag" `
    "$root"
}

docker build `
  -f "$root\deploy\docker\Dockerfile.frontend" `
  -t "$imagePrefix/wms-frontend`:$tag" `
  "$root"
