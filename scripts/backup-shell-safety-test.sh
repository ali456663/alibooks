#!/bin/sh
set -eu

project_root="$(cd "$(dirname "$0")/.." && pwd)"
test_root="$(mktemp -d "${TMPDIR:-/tmp}/alibooks-shell-test-XXXXXXXX")"
mkdir "$test_root/backups"
printf 'synthetic test only\n' > "$test_root/ca.pem"
cat > "$test_root/docker" <<'MOCK_DOCKER'
#!/bin/sh
case " $* " in
  *" pg_dump "*)
    while [ "$#" -gt 0 ]; do
      if [ "$1" = "-f" ]; then
        shift
        target="$1"
        break
      fi
      shift
    done
    case "$target" in
      /backups/.alibooks-*/backup.dump)
        printf 'synthetic dump\n' > "$BACKUP_DIR/${target#/backups/}"
        ;;
      *) exit 89 ;;
    esac
    exit "$DUMP_EXIT"
    ;;
  *" pg_restore -l "*) exit "$CATALOG_EXIT" ;;
  *) exit 88 ;;
esac
MOCK_DOCKER
chmod +x "$test_root/docker"

cleanup() {
  rm -f "$test_root/ca.pem" "$test_root/docker" "$test_root/output" "$test_root/backups"/alibooks-*.dump
  rmdir "$test_root/backups" "$test_root"
}
trap cleanup EXIT

run_backup() (
  export DUMP_EXIT="$1" CATALOG_EXIT="$2"
  export PGHOST=remote.invalid PGDATABASE=fixture PGUSER=fixture PGPASSWORD=synthetic
  export RDS_CA_CERT_HOST_PATH="$test_root/ca.pem" BACKUP_DIR="$test_root/backups"
  PATH="$test_root:$PATH" sh "$project_root/scripts/backup-postgres.sh"
)

assert_unpublished() {
  set -- "$test_root/backups"/alibooks-*.dump
  [ ! -e "$1" ] || { echo "An unverified backup was published"; exit 1; }
  set -- "$test_root/backups"/.alibooks-*
  [ ! -e "$1" ] || { echo "A temporary backup directory was left behind"; exit 1; }
}

if run_backup 7 0 > "$test_root/output" 2>&1; then
  echo "A failed dump was accepted"
  exit 1
fi
assert_unpublished

if run_backup 0 8 > "$test_root/output" 2>&1; then
  echo "A failed catalog check was accepted"
  exit 1
fi
assert_unpublished

run_backup 0 0 > "$test_root/output" 2>&1
set -- "$test_root/backups"/alibooks-*.dump
[ "$#" -eq 1 ] && [ -f "$1" ] || { echo "Verified backup was not published"; exit 1; }
set -- "$test_root/backups"/.alibooks-*
[ ! -e "$1" ] || { echo "Verified backup left a temporary directory"; exit 1; }

echo "Shell backup safety: 3/3 passed."
