$ErrorActionPreference = "Stop"
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("alibooks-backup-script-test-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $root | Out-Null
$dump = Join-Path $root "fixture.dump"
Set-Content -LiteralPath $dump -Value "synthetic test only"
$caCert = Join-Path $root "synthetic-ca.pem"
Set-Content -LiteralPath $caCert -Value "synthetic test only"
$global:AliBooksBackupTestState = @{ codes = @(); calls = 0; arguments = @() }

# Shadow Docker only inside this test process. No Docker service or real data.
function docker {
  $global:AliBooksBackupTestState.arguments += ,$args
  $global:LASTEXITCODE = $global:AliBooksBackupTestState.codes[$global:AliBooksBackupTestState.calls]
  $dumpIndex = [Array]::IndexOf($args, 'pg_dump')
  if ($dumpIndex -ge 0) {
    $fileIndex = [Array]::IndexOf($args, '-f')
    $target = $args[$fileIndex + 1]
    if ($fileIndex -lt 0 -or $target -cnotmatch '^/backups/(\.alibooks-[^/]+)/backup\.dump$') {
      throw 'Backup must write into a temporary directory.'
    }
    Set-Content -LiteralPath (Join-Path $root "$($Matches[1])/backup.dump") -Value 'synthetic dump'
  }
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
    & "$PSScriptRoot/backup-postgres.ps1" -PgHost test.invalid -PgDatabase fixture -PgUser fixture -PgPassword synthetic -RdsCaCertHostPath $caCert -BackupDir $root
  }
  $restore = {
    & "$PSScriptRoot/restore-postgres.ps1" -PgHost test.invalid -PgDatabase alibooks_restore_test -PgUser fixture -PgPassword synthetic -RdsCaCertHostPath $caCert -RestoreFile $dump -RestoreConfirm RESTORE_TO_TEST_DATABASE
  }
  Test-Case "remote backup blocks weak TLS" @() 0 "verify-full" {
    & "$PSScriptRoot/backup-postgres.ps1" -PgHost test.invalid -PgDatabase fixture -PgUser fixture -PgPassword synthetic -PgSslMode require -BackupDir $root
  }
  Test-Case "remote restore blocks weak TLS" @() 0 "verify-full" {
    & "$PSScriptRoot/restore-postgres.ps1" -PgHost test.invalid -PgDatabase alibooks_restore_test -PgUser fixture -PgPassword synthetic -PgSslMode require -RestoreFile $dump -RestoreConfirm RESTORE_TO_TEST_DATABASE
  }
  Test-Case "backup requires CA certificate" @() 0 "RDS_CA_CERT_HOST_PATH" {
    & "$PSScriptRoot/backup-postgres.ps1" -PgHost test.invalid -PgDatabase fixture -PgUser fixture -PgPassword synthetic -RdsCaCertHostPath (Join-Path $root "missing.pem") -BackupDir $root
  }
  Test-Case "restore requires CA certificate" @() 0 "RDS_CA_CERT_HOST_PATH" {
    & "$PSScriptRoot/restore-postgres.ps1" -PgHost test.invalid -PgDatabase alibooks_restore_test -PgUser fixture -PgPassword synthetic -RdsCaCertHostPath (Join-Path $root "missing.pem") -RestoreFile $dump -RestoreConfirm RESTORE_TO_TEST_DATABASE
  }
  Test-Case "pg_dump error is fatal" @(7) 1 "pg_dump failed" $backup
  if (Get-ChildItem -LiteralPath $root -Filter '.alibooks-*' -Directory -Force) { throw 'Failed dump left a temporary directory.' }
  Test-Case "catalog error is fatal" @(0, 8) 2 "catalog verification failed" $backup
  if (Get-ChildItem -LiteralPath $root -Filter '.alibooks-*' -Directory -Force) { throw 'Failed catalog check left a temporary directory.' }
  if (Get-ChildItem -LiteralPath $root -Filter 'alibooks-*.dump' -File) { throw 'Failed backup was published as a .dump file.' }
  Test-Case "restore does not start with invalid catalog" @(9) 1 "Restore was not started" $restore
  Test-Case "restore error is fatal" @(0, 10) 2 "Restore failed" $restore
  Test-Case "backup success" @(0, 0) 2 "" $backup
  if ((Get-ChildItem -LiteralPath $root -Filter 'alibooks-*.dump' -File).Count -ne 1) { throw 'Verified backup was not published.' }
  if (Get-ChildItem -LiteralPath $root -Filter '.alibooks-*' -Directory -Force) { throw 'Verified backup left a temporary directory.' }
  if ($global:AliBooksBackupTestState.arguments[0] -notcontains 'PGSSLROOTCERT=/run/certs/rds-ca-bundle.pem' -or
      $global:AliBooksBackupTestState.arguments[0] -notcontains "${caCert}:/run/certs/rds-ca-bundle.pem:ro") {
    throw "Backup must mount the CA certificate read-only in Docker."
  }
  Test-Case "restore success" @(0, 0) 2 "" $restore
  $restoreArgs = $global:AliBooksBackupTestState.arguments[1]
  if ($restoreArgs -notcontains 'PGSSLROOTCERT=/run/certs/rds-ca-bundle.pem' -or
      $restoreArgs -notcontains "${caCert}:/run/certs/rds-ca-bundle.pem:ro") {
    throw "Restore must mount the CA certificate read-only in Docker."
  }
  if ($restoreArgs -contains "--clean" -or $restoreArgs -notcontains "--single-transaction" -or $restoreArgs -notcontains "--exit-on-error") {
    throw "Restore must be transactional and must not delete existing tables."
  }
  Test-Case "production-like name is blocked" @() 0 "Refusing restore" {
    & "$PSScriptRoot/restore-postgres.ps1" -PgHost test.invalid -PgDatabase latest_production -PgUser fixture -PgPassword synthetic -RdsCaCertHostPath $caCert -RestoreFile $dump -RestoreConfirm RESTORE_TO_TEST_DATABASE
  }
  Write-Host "PowerShell backup safety: 11/11 passed."
} finally {
  Remove-Variable -Name AliBooksBackupTestState -Scope Global
  $resolved = [System.IO.Path]::GetFullPath($root)
  $temp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
  if (-not $resolved.StartsWith($temp, [System.StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe temporary cleanup path." }
  Remove-Item -LiteralPath $resolved -Recurse -Force
}
