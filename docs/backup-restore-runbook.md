# Backup och restore for AliBooks

AliBooks ska inte anvandas med riktig kunddata eller bokforingsdata innan backup ar skapad och verifierad.
Detta ska goras innan go-live och upprepas innan stor import, periodlasning, bokslut, migrering eller deploy.

## Innan go-live

1. Kontrollera att RDS automated backups ar pa.
2. Skapa en manuell **RDS snapshot** innan forsta riktiga anvandning.
3. Kor en PostgreSQL-backup med `pg_dump`.
4. Verifiera backupfilen med `pg_restore -l`.
5. Gor en **restore drill** till en separat test database.
6. Spara backup och underlag sakert. Bokforingsdata och underlag ska kunna sparas i minst 7 ar efter rakenskapsarets slut.

## Backup fran EC2/Linux

Kor fran projektroten pa EC2 eller en maskin som far ansluta till databasen:

```bash
export PGHOST=your-rds-endpoint.eu-north-1.rds.amazonaws.com
export PGPORT=5432
export PGDATABASE=cloudshop
export PGUSER=cloudshop
export PGPASSWORD='replace-with-rds-password'

sh ./scripts/backup-postgres.sh
```

Scriptet skapar en `.dump` i `./backups` och verifierar filens katalog med `pg_restore -l`.

## Backup fran Windows/PowerShell

```powershell
$env:PGHOST="your-rds-endpoint.eu-north-1.rds.amazonaws.com"
$env:PGPORT="5432"
$env:PGDATABASE="cloudshop"
$env:PGUSER="cloudshop"
$env:PGPASSWORD="replace-with-rds-password"

.\scripts\backup-postgres.ps1
```

## Restore drill till test database

Do not restore into production first. Aterlas alltid forst till en separat test database.

Skapa eller valj en separat testdatabas, till exempel `alibooks_restore_test`.

### Restore fran EC2/Linux

```bash
export PGHOST=your-rds-endpoint.eu-north-1.rds.amazonaws.com
export PGPORT=5432
export PGDATABASE=alibooks_restore_test
export PGUSER=cloudshop
export PGPASSWORD='replace-with-rds-password'
export RESTORE_FILE='./backups/<backup-file>.dump'
export RESTORE_CONFIRM=RESTORE_TO_TEST_DATABASE

sh ./scripts/restore-postgres.sh
```

### Restore fran Windows/PowerShell

```powershell
$env:PGHOST="your-rds-endpoint.eu-north-1.rds.amazonaws.com"
$env:PGPORT="5432"
$env:PGDATABASE="alibooks_restore_test"
$env:PGUSER="cloudshop"
$env:PGPASSWORD="replace-with-rds-password"
$env:RESTORE_FILE=".\backups\<backup-file>.dump"
$env:RESTORE_CONFIRM="RESTORE_TO_TEST_DATABASE"

.\scripts\restore-postgres.ps1
```

Efter restore drill:

- starta backend mot testdatabasen
- kontrollera `/api/system/status`
- kontrollera kundlista, fakturor, bokforing, momsrapport och rapporter
- radera testdatabasen nar kontrollen ar klar

## Viktigt

- Committa aldrig backupfiler till Git.
- Spara inte backupfiler i frontend eller publik mapp.
- Dela inte backupfiler med AI-verktyg.
- Kryptera eller lagra backupen i en skyddad plats.
- Gor ny backup innan stor import, periodlasning, bokslut, migrering eller deploy.
