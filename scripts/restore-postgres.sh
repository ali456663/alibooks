#!/bin/sh
set -eu

: "${PGHOST:?Set PGHOST to the RDS/PostgreSQL host.}"
: "${PGDATABASE:?Set PGDATABASE to the restore target database name. Use a separate test database.}"
: "${PGUSER:?Set PGUSER to the database user.}"
: "${PGPASSWORD:?Set PGPASSWORD to the database password.}"
: "${RESTORE_FILE:?Set RESTORE_FILE to the .dump file created by backup-postgres.sh.}"
: "${RESTORE_CONFIRM:?Set RESTORE_CONFIRM=RESTORE_TO_TEST_DATABASE to confirm this is not production.}"

PGPORT="${PGPORT:-5432}"
PGSSLMODE="${PGSSLMODE:-verify-full}"

case "$PGHOST" in
  localhost|127.0.0.1|::1|host.docker.internal) ;;
  *)
    if [ "$PGSSLMODE" != "verify-full" ]; then
      echo "Remote PostgreSQL restore requires PGSSLMODE=verify-full."
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

if [ "$RESTORE_CONFIRM" != "RESTORE_TO_TEST_DATABASE" ]; then
  echo "Refusing restore: RESTORE_CONFIRM must be RESTORE_TO_TEST_DATABASE."
  exit 1
fi

case "$PGDATABASE" in
  alibooks_restore_*|alibooks_drill_*)
    ;;
  *)
    echo "Refusing restore: PGDATABASE must start with alibooks_restore_ or alibooks_drill_."
    echo "Use a separate database such as alibooks_restore_test. Do not restore into production first."
    exit 1
    ;;
esac

if [ ! -f "$RESTORE_FILE" ]; then
  echo "Restore file not found: $RESTORE_FILE"
  exit 1
fi

restore_dir_abs="$(cd "$(dirname "$RESTORE_FILE")" && pwd)"
restore_file_base="$(basename "$RESTORE_FILE")"

echo "Verifying backup catalog before restore: ${restore_dir_abs}/${restore_file_base}"
docker run --rm \
  "$@" \
  -v "${restore_dir_abs}:/backups" \
  postgres:16 \
  pg_restore -l "/backups/${restore_file_base}" >/dev/null

echo "Restoring into separate test database: ${PGDATABASE}"
docker run --rm \
  "$@" \
  -v "${restore_dir_abs}:/backups" \
  postgres:16 \
  pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" --single-transaction --exit-on-error --no-owner --no-privileges "/backups/${restore_file_base}"

echo "Restore drill completed for ${PGDATABASE}."
echo "Start the backend against this test database and verify system status, customers, invoices, bookkeeping and VAT report."
