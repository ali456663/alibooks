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
Nya manuella verifikationsunderlag sparas i `voucher_evidence` och
`voucher_evidence_contents` i databasen och foljer darfor med databasdumpen.
Den isolerade restore-kontrollen beraknar SHA-256 pa de aterlasta bytesen,
jamfor filstorlek och stoppar saknade, skadade, foraldralosa eller felkopplade
bilagor. `voucherEvidence.archivedVouchersVerified` anger hur manga arkiverade
underlag som kontrollerats. `voucherEvidence.vouchersMissingEvidence` raknar
historiska M/IB-verifikat som helt saknar bilaga; de rapporteras men gor inte
sjalva backupen omojlig. Varje sadan post maste granskas och kompletteras
fore skarp bokforing. Restore-kontrollen bevisar inte att originalunderlagets
innehall eller ursprungligt digitalt format ar korrekt.
`voucherEvidence.archiveSchema=legacy_absent` visar att kopian togs fore
bilagemigreringen; en halvinstallerad migration stoppas. En sadan backup kan
bevaras som aterstallningspunkt men ar inte bevis pa go-live-klar bokforing.
Stoppa samtliga appinstanser, schemalagda jobb och andra skrivare medan databasdump
och filkopia tas fran samma tillfalle. PostgreSQL far vara igang. En kontroll av
`pg_restore -l` verifierar bara katalogen, inte att databasen och filerna kan aterlasas.

### Samlad produktionsbackup (named volume + RDS)

Stoppa **alla** backendinstanser, schemalagda jobb och andra databasskrivare.
Lat PostgreSQL och den stoppade backend-containern finnas kvar. Satt PGHOST,
PGPORT, PGDATABASE, PGUSER, PGPASSWORD och RDS_CA_CERT_HOST_PATH i miljo utanfor
Git. PGHOST ska vara fjarrdatabasens DNS-namn och certifikatet ska vara en
betrodd CA-fil. Kor fran samma Docker-host som backend-containern:

`npm run backup:production -- <stoppad-backend-container> <ny-backupmapp-utanfor-repot> --writers-stopped`

Verktyget kraver att containern ar stoppad och har en named volume pa
`/app/uploads`. Det kopierar volymen read-only med `--volumes-from`, dumpar RDS
med `verify-full`, aterlaser i en isolerad PostgreSQL-container och jamfor
tabellernas radantal fore/efter samt kvittohashar. Ett `verified-manifest.json`
skapas bara nar alla kontroller ar godkanda. Dessutom maste PostgreSQLs WAL-position
vara oforandrad genom hela backup- och restore-kontrollen: en uppdatering med
oforandrat radantal ska inte godkannas. WAL ar databasserverns gemensamma logg,
sa annan aktivitet pa samma server kan ocksa stoppa backupen; utred orsaken och
kor om med stoppade skrivare, men kringga inte kontrollen. En misslyckad korning kan lamna en
ofullstandig mapp utan manifest; granska orsaken innan ny korning. Flaggan ar en
operatorbekraftelse, inte automatisk avstangning av andra skrivare. Kopiera det
godkanda paketet till skyddad, krypterad lagring pa annan plats och prova appen
mot aterlast kopia innan driftstart.

Produktionsmanifestets `accountingEvidence.reviewRequired` skiljer ett tekniskt
aterlasbart paket fran kvarvarande underlagsbrister. `reasons` markerar
kostnader utan kvitto, okopplade kvittofiler, saknat bilagearkiv, manuella
verifikat utan underlag, saknat fakturaoriginalarkiv och utfardade fakturor utan
arkiverat original. Aterlasningen kontrollerar varje arkiverad faktura-PDF:s
format, storlek och SHA-256 mot databasens originalpost. Ett skadat original
stoppar verifieringen; en historiskt saknad originalpost rapporteras for
manuell utredning. Leveransjournalen for fakturamejl kontrolleras ocksa:
bilagans byteinnehall och hash verifieras. `PENDING` och `UNCERTAIN` ar
olosta och markeras for utredning. `NOT_DELIVERED` ar ett avslutat
avstamningsutfall, inte en skickad faktura. Aterlasningen kraver att varje
sadant beslut har en matchande auditpost med forsoks-id, faktura-id,
namngiven aktor och motivering; granska ocksa om det externa SMTP-underlaget
verkligen styrker beslutet. Okanda statusar eller
motstridiga tidsstamplar stoppar verifieringen. Alla leveransforsok utan
befintlig faktura, samt fakturamejl utan arkiverat original eller med en annan
bilaga an originalet, markeras for utredning. Saknad leveransjournal ar en go-live-sparr aven om en aldre backup
gar att aterlasa. Ett verifierat restore med `reviewRequired=true` far
inte anvandas som go-live-bevis; komplettera och granska posterna forst. Aven
`reviewRequired=false` ar bara en kontroll av dessa kanda luckor, inte ett
redovisningsgodkannande eller bevis pa att originalhandlingarna ar riktiga.

Efter kopiering till separat lagring, verifiera paketet igen mot manifestet:
`npm run verify:backup:bundle -- <backupmapp> --strict`. Detta kontrollerar
dumpens och varje kvittos SHA-256, att inga filer tillkommit eller saknas,
gor en ny isolerad aterlasning och jamfor den med kallans radantal och sparade
kontrollresultat. Strikt lage kraver ocksa produktionsmanifest med oforandrad
WAL-position, inga kanda underlagsluckor och inga sokvagar som maste flyttas.
Det ar fortfarande inte ett signerat eller krypterat bevis: skydda kopian,
manifestet och en oberoende kontrollsumma utanfor samma lagringsplats.

En stoppad containers monterade named volume ska inte lasas med `docker cp`:
backupkommandot monterar volymen explicit. Stegen nedan med `docker cp` galler
endast migrering av gamla containerfiler **innan** named volume infors.

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
minor-unit-exponent 2, alla definierade legacy/minor-skalarpar utan avvikelse
(aven leverantorsbetalningar), och balanserade verifikat i `debit_minor` och
`credit_minor`. En journalrad med negativt belopp eller bade debet och kredit
positiva stoppas aven om verifikatet som helhet balanserar. Belopp som kraver
avrundning godkanns inte. Restore-kontrollen
kraver nu ocksa att de atta operativa pengatabellerna har aktiverade
INSERT/UPDATE-sparrar kopplade till ratt integritetsfunktion. En saknad eller
avstangd trigger stoppar aterlasningsgodkannandet, aven om historiska belopp
fortfarande stammer. Detta kontrollerar installationen, inte att varje framtida
skrivvag eller funktionens implementation ar korrekt; kodtest och driftkontroll
behovs fortfarande. Restore-kontrollen
kraver ocksa att varje bokfort verifikat har en forsluten post i
`journal_voucher_registry`, utan foraldralosa registerposter. Den senaste fulla
noll-avvikelse-korningen maste vara gjord efter att journalens integritetssparr
och verifikationsforslutning installerades. En aldre dump utan detta migreringsschema ska darfor stoppas i
stallet for att godkannas som en komplett aterstallningskandidat.

I den isolerade aterstallningen provas att auditloggen avvisar `TRUNCATE` och
att fakturaoriginal samt metadata och innehall for verifikationsunderlag
avvisar bade `UPDATE` och `DELETE`. En installerad men verkningslos trigger
far inte ge ett godkant resultat. Om ett arkiv ar tomt kan dess beteende inte
provas mot en befintlig rad; da kontrolleras bara att ratt trigger ar aktiverad.
Detta ar inte ett godkannande av produktionsmiljon eller juridisk efterlevnad.

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
export RDS_CA_CERT_HOST_PATH=/protected/certs/rds-ca-bundle.pem

sh ./scripts/backup-postgres.sh
```

Scriptet skapar en `.dump` i `./backups` och verifierar filens katalog med `pg_restore -l`.
En backup skrivs forst till en unik tillfallig mapp och far sitt slutliga
`.dump`-namn forst efter godkand katalogkontroll. Vid fel tas den ofullstandiga
filen bort. Detta ar inte ett restore-test och skyddar inte kvittofilerna.
Backup och restore anvander `PGSSLMODE=verify-full` som standard och kraver
`RDS_CA_CERT_HOST_PATH` till en lasbar CA-certifikatfil pa vardmaskinen. Filen
monteras read-only i den tillfalliga PostgreSQL-containern. Fjarrdatabaser blockerar
svagare TLS-lagen; endast lokal PostgreSQL kan anvanda ett uttryckligt
`PGSSLMODE=disable` for utveckling. Detta krypterar transporten och verifierar
serverns identitet, men dumpfilen maste fortfarande lagras krypterat.

## Backup fran Windows/PowerShell

```powershell
$env:PGHOST="your-rds-endpoint.eu-north-1.rds.amazonaws.com"
$env:PGPORT="5432"
$env:PGDATABASE="cloudshop"
$env:PGUSER="cloudshop"
$env:PGPASSWORD="replace-with-rds-password"
$env:RDS_CA_CERT_HOST_PATH="C:\protected\certs\rds-ca-bundle.pem"

.\scripts\backup-postgres.ps1
```

`verify-full` ar standard aven pa Windows. Ange en riktig, betrodd CA-fil innan
kommandot kors. Dumpfilen ar inte krypterad i vila av skriptet.

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
export RDS_CA_CERT_HOST_PATH=/protected/certs/rds-ca-bundle.pem
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
$env:RDS_CA_CERT_HOST_PATH="C:\protected\certs\rds-ca-bundle.pem"
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
