param(
  [Parameter(Mandatory = $true)]
  [string] $FrontendUrl,

  [Parameter(Mandatory = $true)]
  [string] $BackendUrl
)

$ErrorActionPreference = "Stop"

Write-Host "Checking frontend: $FrontendUrl"
$frontendResponse = Invoke-WebRequest -Uri $FrontendUrl -UseBasicParsing
if ($frontendResponse.StatusCode -ne 200) {
  throw "Frontend check failed. HTTP status: $($frontendResponse.StatusCode)"
}
Write-Host "Frontend OK"

Write-Host "Checking backend health: $BackendUrl/health"
$health = Invoke-RestMethod -Uri "$BackendUrl/health"
if ($health.status -ne "ok") {
  throw "Backend health check failed."
}
Write-Host "Backend health OK"

Write-Host "Checking system status: $BackendUrl/system/status"
$systemStatus = Invoke-RestMethod -Uri "$BackendUrl/system/status"
if (-not $systemStatus.database.ok) {
  throw "Database check failed or database is not reachable."
}
Write-Host "Database OK"

Write-Host "Production smoke test passed."
