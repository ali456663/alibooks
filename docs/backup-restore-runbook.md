# Backup och restore for AliBooks

AliBooks ska inte anvandas med riktig kunddata eller bokforingsdata innan backup ar skapad och verifierad.
Detta ska goras innan go-live och upprepas innan stor import, periodlasning, bokslut, migrering eller deploy.

## Innan go-live

### Samlad lokal backup

`npm run backup:local -- <databascontainer> <underlagsmapp> <ny-backupmapp> --writers-stopped`
tar en lokal cloudshop-dump, kopierar samtliga filer i underlagsmappen och gor
en isolerad aterlasning. Stoppa CloudshopApplication och alla andra skrivare forst;
bekraftelseflaggan ar en operatorbekraftelse, inte automatisk pausning av appen.
Lat PostgreSQL vara igang. Anvand en ny mapp under den Git-ignorerade backups-mappen.
Foraldramappen maste finnas. Kallans filer och databas andras inte.

Verktyget jamfor alla public-tabellers radantal fore/efter backup med aterlast
kopia, kontrollerar filhashar och verifikationsbalans. Det skapar
verified-manifest.json endast efter godkanda kontroller. Befintliga backupmappar
skrivs inte over. En avbruten korning kan lamna en ofullstandig mapp utan manifest;
den far inte betraktas som verifierad.

archiveFilesVerified ar antal aterlasta filer; receiptsVerified ar antal filer
med verifierad databaskoppling. unreferencedFiles och expensesWithoutReceipts
visar kvarvarande avstamningsbehov. En fil utan databaskoppling far inte automatiskt
kopplas till en kostnad. Saknad koppling med befintlig hash stoppar verifieringen.
relocatedPaths visar fortsatt behov av sokvagsmigrering for appens nedladdning.

Backupen innehaller personuppgifter och ar inte krypterad av verktyget.
Git-ignore ar inte atkomstskydd. Spara aven en skyddad, krypterad kopia pa annan
lagringsplats och testa appen mot aterlast data innan skarp anvandning.
Radantal och hashar bevisar inte att originalets bokforing var korrekt.

**Databasbackup innehaller inte kvittofiler.** `ExpenseController` sparar dem i
`uploads/receipts` relativt backendens arbetskatalog. Lokalt ar det normalt
`backend/uploads/receipts`; i produktionscontainern `/app/uploads/receipts`.
Stoppa samtliga appinstanser, schemalagda jobb och andra skrivare medan databasdump
och filkopia tas fran samma tillfalle. PostgreSQL far vara igang. En kontroll av
`pg_restore -l` verifierar bara katalogen, inte att databasen och filerna kan aterlasas.

### Kvitton vid uppgradering av befintlig container

Produktions-compose har nu en named volume pa `/app/uploads`. Den skyddar mot
containerbyte men ar INTE en backup och skyddar inte mot serverhaveri eller
`docker compose down -v`. Behall samma Compose-projektnamn vid varje deploy.

**Kor inte `up --force-recreate`, `down` eller ny deploy pa en gammal container
innan dess kvittofiler har kopierats.** Den nya volymen kan annars dolja dem.
`ec2-deploy.sh` blockerar nu om en befintlig backend saknar denna volym.

Migrera pa servern med ratt Compose-projektnamn och env-fil:

1. Stoppa backend och andra skrivare, men radera inte containern.
2. Hamta dess ID med `docker compose --env-file .env -p alibooks -f docker-compose.prod.yml ps -a -q backend`.
3. Kopiera `/app/uploads/.` fran detta ID med `docker cp` till en ny skyddad backupmapp.
   Om katalogen saknas: kontrollera uttryckligen att databasen saknar kvittoreferenser.
4. Ta databasbackupen medan skrivarna fortfarande ar stoppade.
5. Fyll den nya volymen fran filkopian via en tillfallig backend-container med
   `docker compose run --rm --no-deps --entrypoint sh`, samma projektnamn och compose-fil,
   och backupmappen monterad read-only. Kopiera filerna till `/app/uploads`.
   Starta inte Java i denna hjalpcontainer och skriv aldrig over en redan fylld volym.
6. Aterlas och verifiera backupen enligt nedan. Starta sedan backend och prova
   nedladdning av kvitton via appen. Spara den ursprungliga filkopian separat.

### Automatisk isolerad aterlasning

```powershell
npm run verify:backup -- "C:/protected-backup/database.dump" "C:/protected-backup/uploads/receipts"
```

Samma kommando fungerar pa Linux med Linux-sokvagar. Verktyget anvander PostgreSQL
16 i en NY tillfallig container, utan natverk, publicerade portar, vardmonteringar
eller produktionsuppgifter. Databasen aterlases atomart. Samtliga filer kopieras
och deras SHA-256 kontrolleras efter kopiering. For databaskopplade kvitton jamfors
SHA-256 aven med databasen. Verifikationer
kontrolleras for balans och negativa eller saknade debet/kreditvarden. Containern
tas bort aven vid fel; ursprungliga backupfiler andras aldrig.

Restore-verifieringen kraver dessutom den versionssatta beloppsmodellen: SEK med
minor-unit-exponent 2, alla definierade legacy/minor-skalarpar utan avvikelse,
verifierad noll-avvikelse-korning och balanserade verifikat i `debit_minor` och
`credit_minor`. En aldre dump utan detta migreringsschema ska darfor stoppas i
stallet for att godkannas som en komplett aterstallningskandidat.

Saknade filer/hashar, felaktiga hashvarden, skadad dump, beloppsavvikelse och
obalans ger exitkod 1.
`relocatedPaths` over noll betyder att lagrade sokvagar inte matchar containerplatsen:
filernas innehall ar kontrollerat men appens nedladdning ar INTE verifierad. Flytt fran
Windows till Linux kraver separat kontrollerad sokvagsmigrering. Verktyget andrar
inte lagrade sokvagar automatiskt. Kontrollsummor upptacker korruption, inte en angripare
som andrat bade filer och databas. Anvand bara betrodda egna backupfiler.

`npm run test:backup` provar verktyget med syntetiska databas-/fil-fixtures.
Det provar inte hela appens schema, alla affarsregler eller din verkliga backup.
En tom databas kan aterlasas korrekt utan att vara en komplett verksamhetsbackup.
Jamfor alltid antal poster mot kallan och prova appens floden mot aterlast kopia.
Spara resultat, backupdatum, kallmiljo och granskarens beslut utan personuppgifter.

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

Skapa en tom separat testdatabas, till exempel `alibooks_restore_test`.
Manuella aterlasningsskript accepterar bara namn med prefix `alibooks_restore_`
eller `alibooks_drill_`. De raderar inte befintliga tabeller; fel avbryter hela
transaktionen. Det isolerade verktyget ovan ar forstahandsvalet for verifiering.

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
