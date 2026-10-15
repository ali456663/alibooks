param(
  [string] $PgHost = $env:PGHOST,
  [string] $PgDatabase = $env:PGDATABASE,
  [string] $PgUser = $env:PGUSER,
  [string] $PgPassword = $env:PGPASSWORD,
  [string] $PgPort = $(if ($env:PGPORT) { $env:PGPORT } else { "5432" }),
  [string] $PgSslMode = $(if ($env:PGSSLMODE) { $env:PGSSLMODE } else { "verify-full" }),
  [string] $RdsCaCertHostPath = $env:RDS_CA_CERT_HOST_PATH,
  [string] $BackupDir = $(if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { ".\backups" })
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PgHost)) { throw "Set PGHOST to the RDS/PostgreSQL host." }
if ([string]::IsNullOrWhiteSpace($PgDatabase)) { throw "Set PGDATABASE to the database name." }
if ([string]::IsNullOrWhiteSpace($PgUser)) { throw "Set PGUSER to the database user." }
if ([string]::IsNullOrWhiteSpace($PgPassword)) { throw "Set PGPASSWORD to the database password." }
if ($PgDatabase -cnotmatch '^[A-Za-z0-9_-]+$') { throw "PGDATABASE contains characters unsafe for a backup filename." }
if ($PgHost -notin @("localhost", "127.0.0.1", "::1", "host.docker.internal") -and $PgSslMode -ne "verify-full") {
  throw "Remote PostgreSQL backup requires PGSSLMODE=verify-full."
}
$dockerConnectionArgs = @('-e', "PGPASSWORD=$PgPassword", '-e', "PGSSLMODE=$PgSslMode")
if ($PgSslMode -eq "verify-full") {
  if ([string]::IsNullOrWhiteSpace($RdsCaCertHostPath) -or -not (Test-Path -LiteralPath $RdsCaCertHostPath -PathType Leaf)) {
    throw "RDS_CA_CERT_HOST_PATH must point to an existing PostgreSQL CA certificate file."
  }
  $caCertAbs = (Resolve-Path -LiteralPath $RdsCaCertHostPath).Path
  $dockerConnectionArgs += @('-e', 'PGSSLROOTCERT=/run/certs/rds-ca-bundle.pem', '-v', "${caCertAbs}:/run/certs/rds-ca-bundle.pem:ro")
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$backupDirAbs = (Resolve-Path $BackupDir).Path
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$nonce = [guid]::NewGuid().ToString("N")
$partialName = ".alibooks-$PgDatabase-$timestamp-$nonce"
$partialDir = Join-Path $backupDirAbs $partialName
$partialFile = Join-Path $partialDir "backup.dump"
$filename = "alibooks-$PgDatabase-$timestamp-$nonce.dump"
$finalFile = Join-Path $backupDirAbs $filename
New-Item -ItemType Directory -Path $partialDir -ErrorAction Stop | Out-Null

try {
  Write-Host "Creating PostgreSQL custom-format backup in a temporary directory"
  docker run --rm `
    @dockerConnectionArgs `
    -v "${backupDirAbs}:/backups" `
    postgres:16 `
    pg_dump -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase -Fc -f "/backups/$partialName/backup.dump"
  if ($LASTEXITCODE -ne 0) { throw "pg_dump failed. Backup is NOT verified." }

  Write-Host "Verifying backup catalog with pg_restore -l"
  docker run --rm `
    @dockerConnectionArgs `
    -v "${backupDirAbs}:/backups" `
    postgres:16 `
    pg_restore -l "/backups/$partialName/backup.dump" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Backup catalog verification failed." }

  if (-not (Test-Path -LiteralPath $partialFile -PathType Leaf)) { throw "pg_dump did not produce a backup file." }
  if (Test-Path -LiteralPath $finalFile) { throw "Refusing to overwrite an existing backup file." }
  Move-Item -LiteralPath $partialFile -Destination $finalFile -ErrorAction Stop
  Write-Host "Backup verified (catalog only, not a restore test): $finalFile"
  Write-Host "Also copy uploads/receipts while all writers are stopped. A database dump does not contain receipt files."
  Write-Host "Store this file securely and never commit it to Git."
} finally {
  if (Test-Path -LiteralPath $partialFile) { Remove-Item -LiteralPath $partialFile -Force }
  if (Test-Path -LiteralPath $partialDir) { Remove-Item -LiteralPath $partialDir -Force }
}
