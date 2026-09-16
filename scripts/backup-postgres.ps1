param(
  [string] $PgHost = $env:PGHOST,
  [string] $PgDatabase = $env:PGDATABASE,
  [string] $PgUser = $env:PGUSER,
  [string] $PgPassword = $env:PGPASSWORD,
  [string] $PgPort = $(if ($env:PGPORT) { $env:PGPORT } else { "5432" }),
  [string] $BackupDir = $(if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { ".\backups" })
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PgHost)) { throw "Set PGHOST to the RDS/PostgreSQL host." }
if ([string]::IsNullOrWhiteSpace($PgDatabase)) { throw "Set PGDATABASE to the database name." }
if ([string]::IsNullOrWhiteSpace($PgUser)) { throw "Set PGUSER to the database user." }
if ([string]::IsNullOrWhiteSpace($PgPassword)) { throw "Set PGPASSWORD to the database password." }

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$backupDirAbs = (Resolve-Path $BackupDir).Path
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$filename = "alibooks-$PgDatabase-$timestamp.dump"

Write-Host "Creating PostgreSQL custom-format backup: $backupDirAbs\$filename"
docker run --rm `
  -e "PGPASSWORD=$PgPassword" `
  -v "${backupDirAbs}:/backups" `
  postgres:16 `
  pg_dump -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase -Fc -f "/backups/$filename"
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed. Backup is NOT verified." }

Write-Host "Verifying backup catalog with pg_restore -l"
docker run --rm `
  -e "PGPASSWORD=$PgPassword" `
  -v "${backupDirAbs}:/backups" `
  postgres:16 `
  pg_restore -l "/backups/$filename" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Backup catalog verification failed." }

Write-Host "Backup verified (catalog only, not a restore test): $backupDirAbs\$filename"
Write-Host "Also copy uploads/receipts while all writers are stopped. A database dump does not contain receipt files."
Write-Host "Store this file securely and never commit it to Git."
