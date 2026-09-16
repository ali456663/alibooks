$ErrorActionPreference = "Stop"
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("alibooks-backup-script-test-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $root | Out-Null
$dump = Join-Path $root "fixture.dump"
Set-Content -LiteralPath $dump -Value "synthetic test only"
$global:AliBooksBackupTestState = @{ codes = @(); calls = 0; arguments = @() }

# Shadow Docker only inside this test process. No Docker service or real data.
function docker {
  $global:AliBooksBackupTestState.arguments += ,$args
  $global:LASTEXITCODE = $global:AliBooksBackupTestState.codes[$global:AliBooksBackupTestState.calls]
  $global:AliBooksBackupTestState.calls++
}
function Test-Case($name, $codes, $expectedCalls, $expectedError, [scriptblock] $action) {
  $global:AliBooksBackupTestState.codes = $codes
  $global:AliBooksBackupTestState.calls = 0
  $global:AliBooksBackupTestState.arguments = @()
  $message = ""
  try { & $action } catch { $message = $_.Exception.Message }
  if ($expectedError -and $message -notmatch $expectedError) { throw "$name did not reject correctly: $message" }
  if (-not $expectedError -and $message) { throw "$name failed: $message" }
  if ($global:AliBooksBackupTestState.calls -ne $expectedCalls) { throw "${name}: unexpected Docker call count" }
  Write-Host "PASS: $name"
}
try {
  $backup = {
    & "$PSScriptRoot/backup-postgres.ps1" -PgHost test.invalid -PgDatabase fixture -PgUser fixture -PgPassword synthetic -BackupDir $root
  }
  $restore = {
    & "$PSScriptRoot/restore-postgres.ps1" -PgHost test.invalid -PgDatabase alibooks_restore_test -PgUser fixture -PgPassword synthetic -RestoreFile $dump -RestoreConfirm RESTORE_TO_TEST_DATABASE
  }
  Test-Case "pg_dump error is fatal" @(7) 1 "pg_dump failed" $backup
  Test-Case "catalog error is fatal" @(0, 8) 2 "catalog verification failed" $backup
  Test-Case "restore does not start with invalid catalog" @(9) 1 "Restore was not started" $restore
  Test-Case "restore error is fatal" @(0, 10) 2 "Restore failed" $restore
  Test-Case "backup success" @(0, 0) 2 "" $backup
  Test-Case "restore success" @(0, 0) 2 "" $restore
  $restoreArgs = $global:AliBooksBackupTestState.arguments[1]
  if ($restoreArgs -contains "--clean" -or $restoreArgs -notcontains "--single-transaction" -or $restoreArgs -notcontains "--exit-on-error") {
    throw "Restore must be transactional and must not delete existing tables."
  }
  Test-Case "production-like name is blocked" @() 0 "Refusing restore" {
    & "$PSScriptRoot/restore-postgres.ps1" -PgHost test.invalid -PgDatabase latest_production -PgUser fixture -PgPassword synthetic -RestoreFile $dump -RestoreConfirm RESTORE_TO_TEST_DATABASE
  }
  Write-Host "PowerShell backup safety: 7/7 passed."
} finally {
  Remove-Variable -Name AliBooksBackupTestState -Scope Global
  $resolved = [System.IO.Path]::GetFullPath($root)
  $temp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
  if (-not $resolved.StartsWith($temp, [System.StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe temporary cleanup path." }
  Remove-Item -LiteralPath $resolved -Recurse -Force
}
