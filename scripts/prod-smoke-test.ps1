param(
  [Parameter(Mandatory = $true)]
  [string] $FrontendUrl,

  [Parameter(Mandatory = $true)]
  [string] $BackendUrl,

  [Parameter(Mandatory = $false)]
  [string] $AuthToken = ""
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

Write-Host "Checking protected system status: $BackendUrl/system/status"
if ([string]::IsNullOrWhiteSpace($AuthToken)) {
  try {
    Invoke-WebRequest -Uri "$BackendUrl/system/status" -UseBasicParsing | Out-Null
    throw "System status protection check failed. Expected HTTP 401 without AuthToken."
  } catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 401) {
      throw
    }
  }
  Write-Host "System status protected (HTTP 401 without AuthToken)"
  Write-Host "Pass -AuthToken to verify database.ok in the protected status response."
} else {
  if ($AuthToken.StartsWith("Bearer ")) {
    $authHeader = $AuthToken
  } else {
    $authHeader = "Bearer $AuthToken"
  }
  $headers = @{ Authorization = $authHeader }
  $systemStatus = Invoke-RestMethod -Uri "$BackendUrl/system/status" -Headers $headers
  if (-not $systemStatus.database.ok) {
    throw "Database check failed or database is not reachable."
  }
  Write-Host "Authenticated system status and database OK"
}

Write-Host "Production smoke test passed."
