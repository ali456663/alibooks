#!/bin/sh
set -eu

: "${PGHOST:?Set PGHOST to the RDS/PostgreSQL host.}"
: "${PGDATABASE:?Set PGDATABASE to the database name.}"
: "${PGUSER:?Set PGUSER to the database user.}"
: "${PGPASSWORD:?Set PGPASSWORD to the database password.}"

case "$PGDATABASE" in
  *[!A-Za-z0-9_-]*|'')
    echo "PGDATABASE contains characters unsafe for a backup filename."
    exit 1
    ;;
esac

PGPORT="${PGPORT:-5432}"
PGSSLMODE="${PGSSLMODE:-verify-full}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"

case "$PGHOST" in
  localhost|127.0.0.1|::1|host.docker.internal) ;;
  *)
    if [ "$PGSSLMODE" != "verify-full" ]; then
      echo "Remote PostgreSQL backup requires PGSSLMODE=verify-full."
      exit 1
    fi
    ;;
esac

set -- -e "PGPASSWORD=$PGPASSWORD" -e "PGSSLMODE=$PGSSLMODE"
if [ "$PGSSLMODE" = "verify-full" ]; then
  : "${RDS_CA_CERT_HOST_PATH:?Set RDS_CA_CERT_HOST_PATH to the PostgreSQL CA certificate file.}"
  if [ ! -f "$RDS_CA_CERT_HOST_PATH" ]; then
    echo "RDS_CA_CERT_HOST_PATH must point to an existing certificate file."
    exit 1
  fi
  ca_cert_abs="$(cd "$(dirname "$RDS_CA_CERT_HOST_PATH")" && pwd)/$(basename "$RDS_CA_CERT_HOST_PATH")"
  set -- "$@" -e PGSSLROOTCERT=/run/certs/rds-ca-bundle.pem -v "${ca_cert_abs}:/run/certs/rds-ca-bundle.pem:ro"
fi

mkdir -p "$BACKUP_DIR"
backup_dir_abs="$(cd "$BACKUP_DIR" && pwd)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
partial_dir="$(mktemp -d "${backup_dir_abs}/.alibooks-${PGDATABASE}-${timestamp}-XXXXXXXX")"
partial_name="$(basename "$partial_dir")"
filename="${partial_name#.}.dump"
cleanup() {
  rm -f "$partial_dir/backup.dump"
  rmdir "$partial_dir" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

echo "Creating PostgreSQL custom-format backup in a temporary directory"
docker run --rm \
  "$@" \
  -v "${backup_dir_abs}:/backups" \
  postgres:16 \
  pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -Fc -f "/backups/${partial_name}/backup.dump"

echo "Verifying backup catalog with pg_restore -l"
docker run --rm \
  "$@" \
  -v "${backup_dir_abs}:/backups" \
  postgres:16 \
  pg_restore -l "/backups/${partial_name}/backup.dump" >/dev/null

if [ -e "${backup_dir_abs}/${filename}" ]; then
  echo "Refusing to overwrite an existing backup: ${backup_dir_abs}/${filename}"
  exit 1
fi
mv "$partial_dir/backup.dump" "${backup_dir_abs}/${filename}"

echo "Backup verified (catalog only, not a restore test): ${backup_dir_abs}/${filename}"
echo "Also copy uploads/receipts while all writers are stopped. A database dump does not contain receipt files."
echo "Store this file securely and never commit it to Git."
