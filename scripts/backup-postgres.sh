#!/bin/sh
set -eu

: "${PGHOST:?Set PGHOST to the RDS/PostgreSQL host.}"
: "${PGDATABASE:?Set PGDATABASE to the database name.}"
: "${PGUSER:?Set PGUSER to the database user.}"
: "${PGPASSWORD:?Set PGPASSWORD to the database password.}"

PGPORT="${PGPORT:-5432}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"

mkdir -p "$BACKUP_DIR"
backup_dir_abs="$(cd "$BACKUP_DIR" && pwd)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
filename="alibooks-${PGDATABASE}-${timestamp}.dump"

echo "Creating PostgreSQL custom-format backup: ${backup_dir_abs}/${filename}"
docker run --rm \
  -e PGPASSWORD="$PGPASSWORD" \
  -v "${backup_dir_abs}:/backups" \
  postgres:16 \
  pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -Fc -f "/backups/${filename}"

echo "Verifying backup catalog with pg_restore -l"
docker run --rm \
  -e PGPASSWORD="$PGPASSWORD" \
  -v "${backup_dir_abs}:/backups" \
  postgres:16 \
  pg_restore -l "/backups/${filename}" >/dev/null

echo "Backup verified (catalog only, not a restore test): ${backup_dir_abs}/${filename}"
echo "Also copy uploads/receipts while all writers are stopped. A database dump does not contain receipt files."
echo "Store this file securely and never commit it to Git."
