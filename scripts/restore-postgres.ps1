param(
  [string] $PgHost = $env:PGHOST,
  [string] $PgDatabase = $env:PGDATABASE,
  [string] $PgUser = $env:PGUSER,
  [string] $PgPassword = $env:PGPASSWORD,
  [string] $PgPort = $(if ($env:PGPORT) { $env:PGPORT } else { "5432" }),
  [string] $PgSslMode = $(if ($env:PGSSLMODE) { $env:PGSSLMODE } else { "verify-full" }),
  [string] $RdsCaCertHostPath = $env:RDS_CA_CERT_HOST_PATH,
  [string] $RestoreFile = $env:RESTORE_FILE,
  [string] $RestoreConfirm = $env:RESTORE_CONFIRM
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PgHost)) { throw "Set PGHOST to the RDS/PostgreSQL host." }
if ([string]::IsNullOrWhiteSpace($PgDatabase)) { throw "Set PGDATABASE to the restore target database name. Use a separate test database." }
if ([string]::IsNullOrWhiteSpace($PgUser)) { throw "Set PGUSER to the database user." }
if ([string]::IsNullOrWhiteSpace($PgPassword)) { throw "Set PGPASSWORD to the database password." }
if ($PgHost -notin @("localhost", "127.0.0.1", "::1", "host.docker.internal") -and $PgSslMode -ne "verify-full") {
  throw "Remote PostgreSQL restore requires PGSSLMODE=verify-full."
}
$dockerConnectionArgs = @('-e', "PGPASSWORD=$PgPassword", '-e', "PGSSLMODE=$PgSslMode")
if ($PgSslMode -eq "verify-full") {
  if ([string]::IsNullOrWhiteSpace($RdsCaCertHostPath) -or -not (Test-Path -LiteralPath $RdsCaCertHostPath -PathType Leaf)) {
    throw "RDS_CA_CERT_HOST_PATH must point to an existing PostgreSQL CA certificate file."
  }
  $caCertAbs = (Resolve-Path -LiteralPath $RdsCaCertHostPath).Path
  $dockerConnectionArgs += @('-e', 'PGSSLROOTCERT=/run/certs/rds-ca-bundle.pem', '-v', "${caCertAbs}:/run/certs/rds-ca-bundle.pem:ro")
}
if ([string]::IsNullOrWhiteSpace($RestoreFile)) { throw "Set RESTORE_FILE to the .dump file created by backup-postgres.ps1." }
if ($RestoreConfirm -ne "RESTORE_TO_TEST_DATABASE") { throw "Refusing restore: RESTORE_CONFIRM must be RESTORE_TO_TEST_DATABASE." }

if ($PgDatabase -cnotmatch "^alibooks_(restore|drill)_[a-z0-9_]+$") {
  throw "Refusing restore: use a separate database named alibooks_restore_* or alibooks_drill_*."
}

if (-not (Test-Path -LiteralPath $RestoreFile)) {
  throw "Restore file not found: $RestoreFile"
}

$restorePath = Resolve-Path -LiteralPath $RestoreFile
$restoreDirAbs = Split-Path -Parent $restorePath.Path
$restoreFileBase = Split-Path -Leaf $restorePath.Path

Write-Host "Verifying backup catalog before restore: $restorePath"
docker run --rm `
  @dockerConnectionArgs `
  -v "${restoreDirAbs}:/backups" `
  postgres:16 `
  pg_restore -l "/backups/$restoreFileBase" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Backup catalog verification failed. Restore was not started." }

Write-Host "Restoring into separate test database: $PgDatabase"
docker run --rm `
  @dockerConnectionArgs `
  -v "${restoreDirAbs}:/backups" `
  postgres:16 `
  pg_restore -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase --single-transaction --exit-on-error --no-owner --no-privileges "/backups/$restoreFileBase"
if ($LASTEXITCODE -ne 0) { throw "Restore failed and was rolled back. Use an empty test database." }

Write-Host "Restore drill completed for $PgDatabase."
Write-Host "Start the backend against this test database and verify system status, customers, invoices, bookkeeping and VAT report."
