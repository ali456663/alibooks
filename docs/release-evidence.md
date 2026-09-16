# AliBooks release evidence

## Original-PDF-arkiv och integritetskontroll 2026-09-16

Slutlig `npm run test:integration` passerade: 365 enhetstester och 207
PostgreSQL-integrationstestfall, totalt **572**, utan failures, errors eller
hoppade tester. Testcontainrar och isolerad databas avvecklades med exit code 0.

Nya utstallda fakturor och krediter arkiverar PDF-bytes i invoice_originals
med SHA-256. Nedladdning och e-postbilaga laser samma arkiverade bytes;
betalning och registerandringar kan inte skriva om originalet. Skadad fil eller
kontrollsumma ger HTTP 409 i stallet for tyst nygenerering. Foreign key och
unik faktura-ID skyddar mot orphan/originalersattning. Aldre fakturor utan
arkiv markeras fortsatt rekonstruerade och backfylls inte.

Den forsta arkivmodellen gav Hibernate null identifier i 94 integrationstest-
fall. `@OneToOne/@MapsId` ersattes med explicit faktura-ID och databasens
foreign key; hela sviten kordes om och ar gron. `node scripts/schema-migration-check.mjs`
passerade 5/5 med 292/292 startup-satser. `node scripts/ci-pipeline-check.mjs`
passerade 39/39. Lokal `npm run check:release` passerade med produktionsbuild
och runtime-smoke.

Ingen riktig e-post, anvandarens databas eller migration mot produktion
anvandes. GitHub Actions ar inte verifierad for dessa lokala andringar.
Originalarkivet loser inte att SMTP-acceptans och databascommit ar separata;
en bestaende outbox och avstamning behovs fore skarp drift. Fullt ore-stod,
verifierad historik/ingangsbalans, kontantmetodens bokslut och externa drift-
kontroller kvarstar ocksa. Se [invoice-document-snapshots.md](invoice-document-snapshots.md).

## Fakturadokument, avtalsrollback och e-postordning 2026-09-09

Slutlig `npm run test:integration` passerade: 339 enhetstester och 199
PostgreSQL-integrationstestfall, totalt **538**, utan failures, errors eller
hoppade tester. Detta steg tillfor 22 enhetstestfall och 15 integrationstestfall.
Testcontainrar och deras isolerade databas avvecklades med exit code 0.

Verifierat: registerandringar skriver inte om nya fakturors PDF-uppgifter;
snapshoten aterlasas fran PostgreSQL och arvs av krediten; aldre NULL-snapshot
backfylls inte vid PDF-lasning; stor fakturaexport avvisas utan auditframgang.
Avtalsfaktura, nasta datum och audit rullas tillbaka tillsammans vid loggfel.
Oversized avtal sparas inte. Mejlets transport mockas: kontroller/bokforing
kommer fore SMTP, fel rullar tillbaka och aterutskick av utstalld faktura,
betald faktura eller kredit skapar inga nya journalrader.

Syntetiska partial-payment.pdf och credit.pdf i backend/target/pdf-proof
renderades och granskades visuellt efter slutversionens enhetstester.
Delbetalning 50 av 125 visar 75 kvar; kredit visar -125 utan betalningsbegaran.
CI:s befintliga backendartefakt inkluderar nu dessa PDF-prov.

Slutlig lokal `npm run check:release` passerade inklusive produktionsbuild och
runtime-smoke. Backendtesterna ovan kordes separat; produktionsbilder byggdes
inte. `node scripts/ci-pipeline-check.mjs` passerade 39/39 och
`node scripts/schema-migration-check.mjs` passerade 5/5 med 290/290 SQL-satser.
`git diff --check` passerade. Ingen riktig SMTP, migrering av anvandarens databas,
betalning eller GitHub-push har gjorts i detta steg. GitHub CI ar inte verifierad
for dessa lokala andringar.

Se [invoice-document-snapshots.md](invoice-document-snapshots.md).
**Inte klar som enda bokforingssystem:** fullt ore-stod, verifierad historik och
ingangsbalans, kontantmetodens bokslut, originaldokumentarkiv, SMTP/commit-
atervinning och aterstaende driftkontroller kvarstar. En dokument-snapshot ar
inte ett oforanderligt arkiv av de skickade PDF-bytesen.

## Journalradskoppling for bankavstamning 2026-09-09

Slutlig `npm run test:integration`: 317 enhetstester och 184 PostgreSQL-testfall,
totalt **501**, utan failures, errors eller hoppade tester. Detta steg tillfor
9 enhetstester och 16 integrationstestfall. Nollnetto med saknade kopplingar,
felaktiga kopplingar, unikhet/foreign key, samtidig matchning, audit-rollback,
periodlasning, autentisering, HTTP-svar och additiv/upprepad migrering provas.
Forsta korningen hade ett fel i den nya testfragans kolumnnamn for audit;
event_action rattades och hela sviten kordes om. Testcontainrarna avvecklades.

`npm run test:reports`: 113 godkanda tester. Bankens riktiga React-vy provades
med mockade API-svar i separat webblasarkontext: inget forvalt matchningsval,
409 behaller raden okopplad, lyckad bekraftelse sparar journal-ID utan ny
betalning, och CSV-exporten innehaller bankradens och journalradens ID.
Mobil 390x844 och desktop 1280x900 provas; mobilens historikrad rattades efter
visuell granskning och testet kontrollerar att falt och knappar ryms i raden.

`npm run check:release` passerade, inklusive 26 API-kontrakt och runtime-smoke.
Efter tillagget av CSV-kolumner passerade produktionsbuild och webblasarprov igen.
`git diff --check` passerade. Forsoket med `check:release:full` stoppades vid
Docker-atkomst i sandboxen; backend korningen ovan gjordes sedan separat med
godkand Docker-atkomst. Produktionsbilder har inte byggts i detta steg.

Se [bank-journal-links.md](bank-journal-links.md) for API, migration och handhavande.
Ingen riktig bankrad har kopplats och ingen migrering har korts mot anvandarens
databas. Gamla kopplingar maste granskas uttryckligen. Andringarna ar lokala,
inte pushade; GitHub CI ar inte verifierad for denna version. Befintlig CI kor
de nya Maven-testerna och API-kontrakten; webblasarprovet har korts lokalt.

**Inte redo som enda bokforingssystem:** oren, verifierad historik/ingangsbalans,
klumpsummor, kontantmetodens bokslut och aterstaende driftkontroller kvarstar.

## Atomisk bankimport 2026-09-09

Slutversionens `npm run test:integration` passerade: 308 enhetstester och 168
PostgreSQL-integrationstestfall, totalt 476, utan failures, errors eller hoppade
tester. De 22 nya integrationstestfallen provar atomisk bankbokning, samtidiga
dubbletter, rollback, autentisering, periodlasning, datum och fullstandiga belopp.
Forsta korningen hade ett fel i testklientens hantering av HTTP 401 i streaming
mode; autentiseringsprovet bytte till Java HttpClient och hela sviten kordes om.
Testcontainrarna avvecklades efter korningen med exit code 0.

`npm run test:reports` passerade 113 tester, inklusive 34 nya banktester.
`scripts/bank-import-ui-test.mjs` provade den faktiska React-vyn med testdata
och blockerade alla verkliga backendanrop. Serverfel och 409 behaller bankraden;
lyckad sparning tar bort den och overbetalning stoppas innan anrop. Desktop
1280x900 och mobil 390x844 granskades. Mobilens rutnat och bankknappar rattades
efter att breddprovet hittade overflow. Ingen generell redesign ingar.

Slutlig lokal `npm run check:release` passerade inklusive produktionsbuild,
24 API-kontrakt och runtime-smoke. Datasakerhetskontrollen kontrollerar nu den
flyttade skipped-raderingen i tjansten och dess periodskydd. Dockerproduktionsbilder
byggdes inte; backendtesterna ovan kordes separat fran frontendens release-gate.

Nya bankanrop, bokforing, historik och audit ar en transaktion. Separat POST
av pastadd booked-historik avvisas. CSV-identiteter ar stabila for oforandrade
rader, men ar inte bankens transaktions-ID och migrerar inte gamla rader.
Se [bank-import-atomicity.md](bank-import-atomicity.md) for omfattning och risker.

**Inte redo som enda bokforingssystem:** oren, historiska importer/ingangsbalanser,
fullstandig transaktionsvis bankavstamning och ovriga go-live-kontroller kvarstar.
Ingen riktig betalning, e-post, kostnad eller bokforingspost skapades i detta steg.
Andringarna ar lokala, inte pushade; GitHub CI ar inte verifierad for denna version.
Backend maste startas om och bankfilen lasas in pa nytt for att prova de nya anropen.

## Verklig lokal backup och isolerad aterlasning 2026-09-09

Efter anvandarens bekraftelse att CloudshopApplication pausats kontrollerades
att port 3000 inte lyssnade. PostgreSQL lamnades igang. `npm run backup:local`
skapade `backups/local-20260909T192913Z/verified-manifest.json` efter godkand
aterlasning i en separat tillfallig PostgreSQL-container utan natverk eller
vardmonteringar. Originaldatabasen och originalfilerna andrades inte.

Verifierat: 74 journalrader, samma radantal i samtliga public-tabeller fore och
efter backup samt i aterlast kopia, och nio kopierade filer med matchande SHA-256.
Verifikationsbalanskontrollen passerade. Manifestet skapades 19:30:01 UTC.
**Nio filer saknar kostnadskoppling och en kostnad saknar kvittoreferens.** Antalet
databaskopplade verifierade kvitton ar darfor noll. Inga kopplingar gissades eller
skapades automatiskt. Filernas affarsmassiga tillhorighet maste granskas separat.

Slutversionens `npm run test:backup` passerade 16/16 syntetiska tester, inklusive
samlad backup, radantalsjamforelse, okopplade filer, saknade referenser och
avvisning av ateranvand backupmapp. `npm run check:backup` passerade 18/18.
Ingen ny backend- eller frontendtestkorning ingar i detta steg.

Backupen ar lokal, Git-ignorerad och inte krypterad av verktyget. En skyddad
kopia pa annan lagringsplats och appens floden mot aterlast data ar fortfarande
inte verifierade. Radantal, filhashar och verifikationsbalans ar inte ett
godkannande av originalbokforingen. Fullt ore-stod och ovriga go-live-blockerare
kvarstar. Kodandringarna ar inte pushade eller verifierade i GitHub CI.

## Samtidig periodlasning och bokforing 2026-09-09

`npm run test:integration` passerade pa slutversionen: 308 enhetstester och
146 PostgreSQL-integrationstestfall, totalt 454, utan failures, errors eller
hoppade tester. Den forsta korningen hittade ett felaktigt SQL-kolumnnamn i ett
nytt test; testfragan rattades till event_action och hela sviten kordes om.
Testcontainrarna avvecklades och kommandot avslutades med exit code 0.

Sju nya integrationstestfall verifierar periodlasning fore bokforing, bokforing
fore periodlasning, gammal JPA-cache, tillaten bokforing efter lasdatum,
samtidiga lasningar, rollback vid auditfel, vantande installningsandring samt
krav pa en yttre transaktion. Tva nya enhetstester kontrollerar refresh med
PESSIMISTIC_WRITE och avvisning nar installningsraden saknas.

`npm run test:reports` passerade 79 frontendtester. Backend wiring och
period-close-kontrollerna passerade. `npm run check:release` passerade inklusive
frontendbuild och runtime-smoke. Produktions-Dockerbilder byggdes inte.

Skyddet ligger bakom befintliga floden och kraver ingen ny meny. En gemensam
databaslasning varar genom kontroll, journalbokforing eller periodlasning till
commit/rollback. Se [period-write-serialization.md](period-write-serialization.md).

Andringarna ar lokala och inte pushade; GitHub CI ar inte verifierad for denna
version. Den riktiga CloudshopApplication startades inte om. Ingen verklig
bokforing, betalning, e-post eller produktionsdatabas andrades.

**Fortfarande inte redo som enda bokforingssystem:** fullt ore-stod,
verifierade importer/ingangsbalanser, kontantmetodens bokslutsflode, verklig
backupaterlasning och ovriga go-live-kontroller kvarstar.

## Exakta importbelopp 2026-09-09

Bank-CSV och SIE-analys har separata testbara moduler. Belopp parsas till BigInt
i oren, utan flyttalsavrundning eller borttagning av godtyckliga tecken. En bankfil
med unsupported oren stoppas helt; SIE visar beloppet men stoppar bokforing.
Gamla cachade SIE-analyser, saknade datum, tvetydiga bankkolumner och forvrangda
belopp godkanns inte av importkontrollen. En ore i differens ar inte balans.

`npm run test:reports` passerade 79 tester (41 nya importtester och 38 tidigare
rapporttester). Detta kommando kors redan i frontendjobbet i CI, men den nya
versionen ar inte pushad och har inte verifierats pa GitHub. Lokal release-gate
passerade inklusive build och runtime-smoke. Backendkoden andrades inte i detta
steg; tidigare 445 backendtestresultat ar inte en ny backendkorning.

Inga verkliga importfiler eller bokforingsposter andrades. Detta steg hindrar
tyst forlust av oren vid dessa importer, men migrerar inte databasen eller andra
beloppsfloden. Fullt ore-stod och ovriga go-live-blockerare aterstar. AliBooks ar
fortfarande inte godkant som enda bokforingssystem. Se money-safety.md.

## Reskontra mot huvudbok 2026-09-09

`npm run test:integration` passerade med 306 enhetstester och 139 integrationstestfall:
445 totalt, inga failures, errors eller hoppade tester. Testcontainrarna avvecklades.
Den nya kontrollen jamfor 1510/2440 per faktura och totalt. Integrationstester
verifierar delbetalningar, kredit/aterbetalning, felkopplade betalningar med oforandrad
totalsumma, saknade kallkopplingar, autentisering och blockerad periodlasning.

`npm run test:reports` passerade 38 frontendtestfall. `npm run check:release`
passerade inklusive produktionsbuild och runtime-smoke. Separat Playwright-prov
`scripts/subledger-ui-test.mjs` passerade med isolerade mockade API-svar:
desktop 1280x800, mobil 375x812, aterforsok, HTTP-fel, ogiltiga rapportsvar,
datumbyte under pagaende hamtning och sessionsbyte. Skarmbilder granskades;
mobilens tabell rullar horisontellt utan att bredda sidan. Testets ursprungliga
Vite/React-importfel rattades i testharnessen innan slutprovet passerade.
Detta ar komponentprov, inte ett fullstandigt inloggat end-to-end-flode.

Under Avstamning finns nu Reskontra mot huvudbok. Fel och ofullstandig historik
ger inte ett godkant saldo. Kontantmetoden ar uttryckligen ej stodd av denna
automatiska jamforelse och blockerar periodlasning tills kontrollerat bokslutsflode
finns. MATCHED ar inte ett bevis pa fullstandiga importer eller skarp driftklarhet.

Andringarna ar lokala, inte pushade. GitHub Actions och produktionsbilder har inte
verifierats for denna version. Den riktiga CloudshopApplication har inte startats om;
inga riktiga bokforingsposter, betalningar, underlag eller e-postutskick andrades.

**Inte godkant som enda bokforingssystem.** Fullt ore-stod, verkliga importer och
ingangsbalanser, kontantmetodens bokslut, transaktionsvis bankmatchning, gemensam
serialisering av periodlasning/bokforing och verklig backupaterlasning aterstar.
Se [subledger-control.md](subledger-control.md) och go-live-riskregister.md.

## Historiska reskontrasaldon 2026-09-09

`npm run test:integration` passerade med 291 enhetstester och 133 integrationstestfall:
424 totalt, inga failures, errors eller hoppade tester. Detta steg lade till
30 enhetstestfall och 10 integrationstestfall jamfort med leverantorsbetalningsskyddet.
Den isolerade PostgreSQL-testmiljon avvecklades efter korningen.

Backendens kund-/leverantorsreskontra och CSV-export beraknar nu saldo fran
fakturadatum, daterade betalningar och krediter/makuleringar. Testerna provar
saldo fore, pa och efter betalningsdatum, fullbetalning, kredit, aterbetalning,
makulering, felaktig historik samt tidigare radering. HTTP/CSV-testfallen provar
verkligt sparade del- och slutbetalningar och att fel inte skriver journal/audit.
Databasfelen i felinjektionstesterna ar avsiktliga och kontrollerar rollback.

Registrerade leverantorsfakturor kan inte raderas, inte heller obetalda
kontantmetodsfakturor. Makulering kravs med datum. Frontendens raderingsknapp och
raderingsfunktion for leverantorsfakturor ar borttagna. Flerradiga betalningsreferenser
avvisas sa att nya referenser inte kan skada det befintliga historikformatet.

`npm run test:reports` passerade 19 frontendtestfall. Sista `npm run check:release`
passerade pa slutversionen, inklusive produktionsbuild och webblasarens runtime-smoke.
Statiska raderingskontroller uppdaterades till den nya striktare bevaranderegeln;
integrationsprovet verifierar att den riktiga databasraden finns kvar efter avvisad
radering. Runtime-smoke testar startsida/inloggningsskal, inte alla inloggade vyer.

Frontend pa http://localhost:5157 gav HTTP 200. Den riktiga CloudshopApplication
har inte startats om. Andringarna ar lokala och inte pushade till GitHub; inga
produktions-Dockerbilder byggdes och inga riktiga bokforingsposter, betalningar,
e-postutskick eller underlag andrades.

**Fortfarande inte godkant som enda bokforingssystem.** Fullt ore-stod,
reskontra/huvudboksavstamning, verifierade importer/ingangsbalanser och verklig
backupaterlasning aterstar. Historiken bygger pa sparade handelser, inte pa
frysta historiska kunduppgifter eller bevisad fullstandighet hos aldre importer.
Se [historical-settlement-reports.md](historical-settlement-reports.md).

## Leverantorsbetalningar och saldokontroller 2026-09-09

`npm run test:integration` passerade med 261 enhetstester och 123 integrationstestfall:
inga failures, errors eller hoppade tester. Detta steg lade till 13 enhetstestfall
och 17 integrationstestfall jamfort med rapportskyddet nedan.

Verifierat mot isolerad PostgreSQL: samtidiga leverantorsbetalningar bevarar bada
beloppen; dubbletter och overbetalningar avvisas; makulering kan inte radera en
betalning och betalning kan inte ateraktivera en makulerad faktura. Journal, saldo,
historik och audit aterstalls tillsammans vid fel. En redan bokford faktura fran
en last period kan betalas pa ett tillatet datum i en oppen period.

Bankavstamning, kund-/leverantorsreskontra och leverantorsexport summerar med
kontrollerade mellanbelopp. Overskriden rapportkapacitet ger HTTP 422 med
REPORT_AMOUNT_LIMIT, inte ett lyckat svar med fel saldo eller en felaktig export.

`npm run test:reports` passerade 19 frontendtestfall. `npm run check:release`
passerade inklusive produktionsbuild och webblasarens runtime-smoke. Produktions-
Dockerbilder byggdes inte i detta steg. Testcontainrarna avvecklades efter testen.
Andringarna ar lokala, inte pushade eller verifierade i GitHub Actions. Den riktiga
CloudshopApplication-processen har inte startats om. Inga riktiga betalningar,
e-postutskick, bokforingsposter eller underlag har andrats.

**Inte klart som enda bokforingssystem:** fullt ore-stod, historiska reskontrasaldon,
transaktionsvis bankmatchning och prov med riktig backup/ingangsbalanser aterstar.
Reskontrans asOfDate styr idag alder men aterstaller inte historiskt saldo.
Se [supplier-payment-safety.md](supplier-payment-safety.md) och
[go-live-riskregister.md](go-live-riskregister.md).

## Rapportskydd 2026-09-09

`npm run test:integration` passerade med 248 enhetstester och 106 integrationstestfall:
inga failures, errors eller hoppade tester. Detta steg lade till 26 enhetstestfall
och 15 HTTP-testfall mot isolerad PostgreSQL, jamfort med foregaende avsnitt.

Verifierat: ackumulerade belopp och differenser kan inte sla runt till fel tecken
i centrala rapporter; negativa resultat bevaras; overskriden beloppsgrans ger
HTTP 422 och REPORT_AMOUNT_LIMIT. Misslyckade HTTP-rapporter/exporter skapar inga
journalposter eller export-audithandelser. Periodlasning upptacker obalans aven
nar en gammal int-summering skulle gett lika debet och kredit.

`npm run test:reports` passerade 19 frontendtestfall for giltiga rapporter,
negativa resultat, felaktig JSON, felpayload och ogiltiga belopps-/radtyper.
Testkommandot ar tillagt i GitHub Actions frontendjobb, men andringarna ar lokala
och har INTE pushats eller verifierats i GitHub Actions i detta steg.

`npm run check:release` passerade inklusive produktionsbuild och webblasarens
runtime-smoke. Gate-korningen omfattar manga statiska kontroller; dessa ersatter
inte integrationstest eller granskning av riktig bokforing. Docker-produktionsbilder
byggdes inte i detta steg. Testdatabas/container avvecklades efter integrationstesten.

Frontend svarar HTTP 200 pa http://localhost:5157. CloudshopApplication maste startas
om for att den riktiga lokala backendprocessen ska ladda andringarna. Riktig
bokforing och historiska underlag har inte andrats.

**Inte klart som enda bokforingssystem:** skydden stoppar belopp utanfor nuvarande
kapacitet men implementerar inte ore-stod. Folj kvarvarande beloppsmigrering,
granskning av ovriga modulers summering, historisk avstamning och verkligt backup-/
betalningsprov i [money-safety.md](money-safety.md).

## Beloppshardning 2026-09-09

Slutkorningen av `npm run test:integration` passerade med 222 enhetstester och
91 integrationstestfall: inga fel, inga errors och inga hoppade tester.
Det ar 37 nya testfall jamfort med Stripe-hardningens 203 + 73. Den exakta
berakningshjalpen jamfors dessutom mot BigDecimal i 2 000 deterministiska fall.

Tester omfattar stora momsmellanprodukter, delbetalning/aterbetalning,
leverantorsmoms, overflow i fakturapris/antal/total, negativ kostnadsmoms,
decimalinput som tidigare kunde kapas, kredit efter prisandring samt
overslag i fler-radiga verifikationer och ingangsbalanser. HTTP-fel kontrolleras
mot den isolerade PostgreSQL-databasen sa att ogiltiga anrop inte sparar bokforing.

`npm run check:release` passerade pa slutversionen inklusive produktionsbuild
och webblasarens runtime-smoke. Testcontainrarna avvecklades. Frontendens lokala
testserver startades pa port 5157 och gav HTTP 200. Riktig bokforing andrades inte.
Backend maste startas om for att ladda andringarna i den vanliga utvecklingsmiljon.
Andringarna ar lokala; ingen ny push eller GitHub-korning ar verifierad har.

Detta ger INTE fullt ore-stod eller ett go-live-godkannande.
[Beloppssakerhet och kvarvarande migrering](money-safety.md) beskriver exakt vad
som fortfarande blockerar skarp anvandning, inklusive rapporternas int-summeringar.

## Backup och kvittoaterlasning 2026-09-09

`npm run test:backup`: 11/11 dynamiska tester godkanda med verklig PostgreSQL 16,
pg_dump, pg_restore och kopiering/hashkontroll av syntetiska kvittofiler.
Provar lyckad aterlasning, saknad/skadad fil, saknad hash, flyttad Windows-sokvag,
skydd mot lasning utanfor backupmappen, obalanserad verifikation och skadad dump.
Tre av fallen kor deployskriptet med simulerad Docker: gammal oskyddad container
blockeras, befintlig kvittovolym och nyinstallation tillats.
`scripts/backup-scripts-test.ps1`: 7/7 tester godkanda med simulerade Docker-exitkoder.
Inga riktiga databaser, kvitton eller nycklar anvandes. Alla testcontainrar avvecklades.

`npm run check:release` passerade inklusive produktionsbuild och browser-smoke.
`check:backup`, `check:ci`, `check:docker` samt shell-syntaxkontroll passerade.
Backendens Maven-tester och produktions-imagebyggen kordes inte om i detta steg;
inga Java-kallfiler andrades. Den nya backupverifieringen kor riktig PostgreSQL.

Produktions-compose lagrar nu uploads i en beststandig volym. Deployskriptet
stoppar uppgradering av gamla containrar utan denna volym tills filer migrerats.
Backupskripten stoppar vid fel och skiljer katalogkontroll fran provad aterlasning.
GitHub Actions har ett nytt obligatoriskt backup/restore-jobb fore Docker build;
en korning pa GitHub av dessa andringar ar annu inte verifierad.

Detta ar INTE ett go-live-godkannande. Kvar: riktig databas+filbackup fran samma
tidpunkt och provade appfloden efter aterlasning, komplett hantering av oren,
Stripe-flode med granskat momsunderlag och verifierade ingangsbalanser/rapporter.
Se [backup-runbook](backup-restore-runbook.md). Gamla absoluta kvittosokvagar
migreras inte automatiskt; de rapporteras som `relocatedPaths`.

## Stripe-hardning 2026-09-09

`npm run test:integration` passerade med 203 enhetstester och 73 integrationstestfall,
utan fel eller hoppade tester. De 19 nya fallen anvander syntetiskt signerade
Stripe-handelser och riktig isolerad PostgreSQL. De provar faktiskt delbetalt
belopp, avrakningskonto 1580, kontantmetoden, ogiltiga belopp/valutor, fordrojd
betalning, event- och sessionsdubbletter, samtidighet med manuella betalningar,
rollback/retry och avvisning av externa kop utan granskat fakturaunderlag.

`npm run check:release` passerade inklusive build och browser-smoke. Inga riktiga
betalningar, Stripe API-anrop eller kundmejl anvandes i dessa tester.
GitHub Actions verifieras efter push. [Kontrakt och skarpa blockerare](stripe-booking-safety.md).

CI-uppfoljning: frontend-smoke laste en annu tom sida efter en fast vantetid.
Smoke vantar nu upp till 25 sekunder pa monterad auth-vy eller crash-fallback,
aven efter reload. De befintliga kontrollerna for blank sida, inloggningsskydd
och aterstallning behalls. Lokal `npm run smoke:runtime` passerade efter fixen.
En separat CI-starttimeout i system-Chrome ledde till att CI nu installerar
versionslast Chrome Headless Shell 153.0.8010.36 fran Googles Chrome for Testing.
Detta paverkar inte anvandarens lokala Chrome-installation.

## Betalningskontroller 2026-09-09

`npm run test:integration` passerade med 203 enhetstester och 54
integrationstestfall: inga fel och inga hoppade tester. De 34 nya fallen provar
ogiltiga belopp, standardbelopp, datum, saknad faktura, samtidiga manuella
betalningar/aterbetalningar/krediteringar och strikt JSON-inlasning via HTTP.
Testdatabas och containrar avvecklades efter korningen.

`npm run check:release` passerade for frontendandringen, inklusive build och
webblasarsmoke. De nya integrationstesterna kors av befintlig CI-profil.
GitHub Actions for denna andring ska kontrolleras efter push.

Detta ar inte ett godkannande for skarp automatisk Stripe-bokforing eller belopp
med oren. Se kvarvarande risker i [databastesternas omfattning](database-integration-tests.md).

## Databasverifiering 2026-09-09

`npm run test:integration` passerade med 203 enhetstester och 20 integrationstester,
utan fel eller hoppade tester. Det tidigare tomma `contextLoads`-testet har ersatts
med riktig Spring Boot-start, PostgreSQL 16 och HTTP-kontroll. Testerna provar
rollback vid databas- och revisionsloggsfel samt samtidiga verifikationsnummer.
Testdatabasen och containrarna avvecklades efter korningen.

`node --test scripts/git-remote.test.mjs` passerade 10/10 fall. Tva CI-kontroller
kravde tidigare exakt HTTPS-adress med `.git`; nu accepteras samma repository
aven utan suffix och via SSH. GitHub Actions for den nya versionen ska verifieras
efter push. Se [databastesternas omfattning](database-integration-tests.md).

`npm run check:release` passerade ocksa 2026-09-09, inklusive frontend-build,
runtime-smoke och samtliga befintliga releasekontroller. Dockerbyggen och GitHub
Actions for denna andring verifieras separat efter push.

Uppfoljning 2026-09-09: GitHub-backendjobbet for `c5142e4` passerade inklusive
integrationstester. Frontendjobbet naddes fram till browser-smoke men kunde inte
ansluta till Chromes debugport. Smoke-testet vantar nu pa browserns beredskap och
visar startdiagnostik. Vite startas direkt som en agd process, sa att testet kan
avslutas utan kvarvarande npm/Vite-processer. Hela frontend-releasekontrollen
passerade darefter i en ren Linux-klon med Chromium. Windows-smoke verifierades
ocksa fran en tom port med automatisk start och avstangning av Vite.

Datumen nedan avser tidigare fullstandiga releasekorningar:

Senast komplett lokalt releasebevis: 2026-09-07 21:48 +02:00.
Senast standard-release och backendtest verifierat: 2026-09-07 21:48 +02:00.
Senast standard-release efter CI-maintenance verifierat: 2026-08-29 22:07 +02:00.
Senast standard-release efter MVP-slutspurt verifierat: 2026-08-30 18:49 +02:00.

## Kommandon som ska vara grona fore push/deploy

Kor fran projektets rotmapp:

```bash
npm run check:release:full
npm run check:bundle
npm run check:frontend-hygiene
npm run check:secrets
npm run check:dependencies
npm run check:env-go-live
npm run check:ci-handoff
npm run check:post-push
npm run check:startklar
npm run check:mvp-use
npm run check:finish-line
npm run check:operations
npm run check:use-today
npm run check:first-real-data
npm run check:pilot
npm run check:calculations
npm run check:retention
npm run check:audit-integrity
npm run check:period-close
npm run check:handoff
npm run check:go-live-decision
npm run check:external-go-live
npm run check:release-traceability
npm run check:migrations
npm run check:schema-bootstrap
npm run check:git
npm run check:sync
npm run check:prepush -- --allow-ahead
```

Detta bevisar lokalt att:

- frontend bygger for produktion
- produktionsbundlen haller MVP-budget och tunga visuella paket ligger i separata chunks
- frontend smoke-test kan rendera appen och kontrollerar att utloggad auth/sprak bara syns pa startsidan
- viktiga frontend/backend API-kontrakt finns kvar
- frontendens produktionskod saknar gamla demo-filer, fristaende landing page-experiment och `alert()`-anrop
- backend-konstruktorer och Java-records matchar testerna
- dokumentation, secrets, Docker-konfig, vyer och produktionsmallar passerar kontroller
- riktiga API-nycklar for Stripe, HF, Google, OpenRouter och OpenAI-liknande providers stoppas av `check:secrets`
- go-live-miljo variabler for JWT, CORS, RDS, schema, Stripe, SMTP och AI ar dokumenterade och kontrollerade
- frontend dependency-lockfile, buildverktyg och Docker-installation kontrolleras statiskt
- CI-handoff efter push ar dokumenterad med GitHub Actions-jobb, Dockerhub-secrets, jobbnamn och felsokning
- Dependabot bevakar frontend npm, backend Maven och GitHub Actions sa beroenderisker inte bara kontrolleras manuellt
- post-push-verifiering skiljer lokal release fran GitHub-sync, Actions, Dockerhub och externa go-live-bevis
- release-version, commit, Dockerhub-taggar och EC2 `IMAGE_TAG` gar att sparas som releasebevis
- startup-schema-patchar ar speglade i kontrollerad SQL-migration innan RDS-deploy
- forsta RDS-basschema har en kontrollerad schema-bootstrap-runbook
- destruktiva raderingar/reset-endpoints har JWT, feature flags, audit och periodlasningsskydd dar det kravs
- revisionsspar har SHA-256-kedja, auditstampel, CSV-export, backupkoppling och backendtest mot andrad historik
- periodstangning har backendkontroll for blockerare, varningar, attest, bankavstamning, momsbevis, periodstampel och slutlig kedjekod
- redovisningspaket kan exportera SIE, kvittenser, huvudbok, saldobalans, rapporter, kontroller och arsarkiv for saker konsultoverlamning
- slutligt go-live-beslut skiljer lokal MVP fran skarp drift och kraver externa bevis innan riktig kunddata
- forsta riktiga data-grinden stoppar om backup, restore drill, testdata, foretagsinstallningar, nummerserier, personuppgifter, betalningsrutin eller export inte ar kontrollerade
- pilotdrift-grinden begransar forsta riktiga veckan till fa kunder, daglig backup, daglig avstamning, manuella klickbevis och tydliga stoppregler
- externa go-live-bevis for GitHub sync, Actions, Dockerhub, EC2/RDS, schema, backup/restore, Stripe, SMTP, bank/Swish/kort, AI och redovisningspaket ar dokumenterade
- backendtester passerar
- backend Docker-image kan byggas
- frontend Docker-image kan byggas

## Senaste lokala bevis

- `npm run check:prepush -- --allow-ahead`: passed 2026-09-07 21:48 +02:00. Full lokal pre-push-kedja passerade med frontend build, bundle 9/9, professionell loop 20/20, SpeedLedger-paritet 28/28, acceptans 18/18, API-kontrakt 22/22, readiness 158/158, evidence 94/94, use-today 34/34, first-real-data 34/34, pilot 25/25, calculations 46/46, retention 23/23, audit-integrity 25/25, period-close 30/30, handoff 29/29, finance UI 100/100, backendtester 204 tests / 0 failures / 0 errors, Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`, samt ren lokal Git-status for viktiga AliBooks-filer. `--allow-ahead` anvandes avsiktligt eftersom branch ligger fore `origin/main` och nasta externa bevis ar push + GitHub Actions.
- `npm run check:release` och `npm run test:backend`: passed 2026-09-07 21:44 +02:00 efter att viktigaste bokforingsmenyn och snabbstarten pa Oversikt verifierades. Standard release gate passerade med frontend build, runtime smoke, vykontroll 49/49, readiness 158/158, evidence 94/94, use-today 34/34, first-real-data 34/34, pilot 25/25, calculations 46/46, retention 23/23, audit-integrity 25/25, period-close 30/30, handoff 29/29, finance UI 100/100 och release traceability 14/14. Backendtester passerade separat via Docker Maven: 204 tests / 0 failures / 0 errors. Traceability varnar korrekt att branch ligger fore `origin/main` och maste pushas innan GitHub Actions kan bevisa senaste versionen.
- `npm run build`, `npm run check:views`, `npm run check:use-today`, `npm run check:evidence`: passed 2026-09-07 21:32 +02:00 efter att viktigaste bokforingsmenyn flyttades hogst upp efter inloggning och Oversikt fick snabbstart till centrala arbetsytor. `check:views` visar 49/49 menyvyer, `check:use-today` visar 34/34 med ny kontroll for synliga professionella huvudval och snabbstart pa Oversikt, och `check:evidence` visar 94/94.
- `npm run check:release`: passed 2026-09-01 19:14 +02:00 efter Startklar-panel for daglig lokal MVP-anvandning, standard release gate med frontend build, runtime smoke, vykontroll 49/49, readiness 158/158, evidence 94/94, use-today 32/32, first-real-data 34/34, finish-line 21/21, finance UI 100/100 och release traceability 14/14. Traceability varnar korrekt att branch ligger fore `origin/main` och maste pushas innan GitHub Actions kan bevisa senaste versionen.
- `npm run check:use-today`: passed 2026-09-01 19:10 +02:00 efter Startklar-panel for daglig lokal MVP-anvandning, 32/32 inklusive synlig Startklar-knapp nara Oversikt, UI-grind for "Kan jag jobba i AliBooks idag?", vit sida/render recovery, backend/databas, berakningar, verifikat, backup, manuell MVP-kontroll, betalningsrutin, sakerhet och produktionsgrans.
- `npm run check:release:full`: passed 2026-08-30 18:56 +02:00 efter MVP-slutspurt-steget, full release gate med frontend build, bundle 9/9, professionell loop 20/20, SpeedLedger-paritet 28/28, acceptans 18/18, readiness 158/158, evidence 94/94, finish-line 20/20, finance UI 100/100, vykontroll 49/49, runtime smoke, backendtester 204 tests / 0 failures / 0 errors och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`.
- `npm run check:release`: passed 2026-08-30 19:23 +02:00 efter Startklar-panel for forsta riktiga data, standard release gate med frontend build, runtime smoke, vykontroll 49/49, readiness 158/158, evidence 94/94, finish-line 21/21, first-real-data 34/34, finance UI 100/100 och release traceability 14/14. Traceability varnar korrekt att branch ar 72 commits fore `origin/main`.
- `npm run check:finish-line`: passed 2026-08-30 19:13 +02:00 efter Startklar-panel for MVP-slutspurt, 21/21 inklusive UI-bevis for 20-stegslistan.
- `npm run check:first-real-data`: passed 2026-08-30 19:18 +02:00 efter Startklar-panel for forsta riktiga data, 34/34 inklusive UI-grind for riktiga kunder, fakturor, kvitton, bankrader och bokforingsposter.
- `npm run check:release`: passed 2026-08-29 22:07 +02:00 efter Dependabot/CI-maintenance-steget, standard release gate med frontend build, runtime smoke, vykontroll, readiness 154/154, CI 39/39, evidence 91/91, env-go-live 39/39 och externa go-live-bevis 29/29.
- `npm run check:calculations`, `npm run check:mvp-use`, `npm run check:use-today`, `npm run check:speedledger-parity`, `npm run check:go-live-risks`, `npm run check:external-go-live`: passed 2026-08-29 22:07 +02:00 som extra snabbkontroll av professionell MVP-kärna efter commit `940cf4f`.
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-28 22:28 +02:00, AliBooks pre-push gate passed, full release gate, runtime smoke, backendtester 204 tests / 0 failures / 0 errors, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`.
- `npm run check:release:full`: passed 2026-08-28 22:28 +02:00 via pre-push gate, frontend build, runtime smoke, backendtester 204 tests / 0 failures / 0 errors och Docker image builds.
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-27 19:25 +02:00, full release gate, runtime smoke, backendtester 204 tests / 0 failures / 0 errors, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`.
- `npm run check:release:full`: passed 2026-08-27 19:25 +02:00 via pre-push gate, frontend build, runtime smoke, backendtester och Docker image builds.
- `npm run check:bundle`: 9/9, passed 2026-08-27, frontend production bundle budget och separata visual/motion/animation chunks verifierade.
- `npm run test:backend`: passed 2026-08-26 14:33 +02:00, 204 tests, 0 failures, 0 errors
- `npm run check:frontend-hygiene`: passed 2026-08-26, inga demo-filer, fristaende landing page-experiment eller `alert()`-anrop i frontendens produktionskod
- `npm run check:release`: passed 2026-08-26, standard gate fran projektroten med frontend build, frontend-hygien, runtime smoke och alla lokala MVP-kontroller
- `npm run check:secrets`: passed 2026-08-26, inga riktiga Stripe-, HF-, Google-, OpenRouter-, OpenAI-liknande, GitHub-, JWT- eller private-key-hemligheter hittades i tracked project files.
- `npm run check:audit`: passed 2026-08-26, 0 vulnerabilities for frontend production dependencies
- `npm run doctor -- --soft`: passed 2026-08-26, PostgreSQL, backend `/health`, backend `/system/status`, database connection and frontend `5157` OK; warnings kvar for lokal `JWT_SECRET` och Docker-behorighet i sandbox.
- `npm run smoke:runtime`: passed 2026-08-26, frontend renderar utan vit sida eller render recovery.
- Rotkommandon verifierade 2026-08-22 21:43 +02:00: `npm run build`, `npm run check:docs`, `npm run check:release` och `npm run test:backend` fungerar fran projektets huvudmapp.
- `npm run test:backend`: passed 2026-08-24, 204 tests, 0 failures, 0 errors
- `npm run check:release`: passed 2026-08-24, standard gate fran projektroten med 18/18 acceptans, 150/150 readiness, 82/82 evidence, 57/57 data safety, 44/44 production readiness, 39/39 env-go-live, 25/25 pilotdrift, 23/23 retention, 25/25 audit-integritet, 30/30 periodstangning, 29/29 redovisningspaket, 17/17 go-live-beslut, 29/29 externa go-live-bevis, 29/29 post-push-verifiering, 33/33 forsta-riktiga-data och runtime smoke
- `npm run check:startklar`: passed 2026-08-24, 20/20, lokal MVP redo enligt kort Startklar-kontroll. Skarp produktion vantar pa GitHub sync, Dockerhub, EC2/RDS, restore drill, Stripe och SMTP.
- `npm run check:env-go-live`: 39/39, go-live-miljo for JWT, lokal JWT-generator, CORS, RDS, schemaflaggor, Stripe, SMTP, AI-nycklar och hemlighetshantering.
- `npm run check:mvp-use`: 20/20, 20-stegs kontroll for anvandningsklar lokal MVP.
- `npm run check:operations`: 23/23, drift-runbook, incidentlogg, releasejournal och rollback-kontroll.
- `npm run check:use-today`: 34/34, slutligt lokalt anvandningsbeslut med synlig Startklar-knapp, viktigaste bokforingsmenyn, snabbstart pa Oversikt, stoppsignaler, vit-sida/render recovery, Startklar-UI och produktionsblockerare.
- `npm run check:first-real-data`: 34/34, forsta riktiga data-grind for backup, restore drill, testdata, foretagsinstallningar, nummerserier, personuppgifter, betalningsrutin och export.
- `npm run check:pilot`: 25/25, begransad pilotdrift for forsta veckan med daglig rutin, backup, restore drill, manuell klickkontroll, export och stoppregler.
- `npm run check:calculations`: 46/46, berakningsintegritet for faktura, moms, delbetalning, Stripe, leverantorer, verifikat och lon-MVP.
- `npm run check:retention`: 23/23, arkiv och andringsspar for fakturor, kunder, leverantorsfakturor, kvitton, bankreset, periodlasning, hard delete, rattelser och backendtester.
- `npm run check:audit-integrity`: 25/25, revisionsspar, SHA-256-kedja, auditstampel, CSV-export, backupkoppling, JWT-krav och backendtester som visar att andrad historik ger ny fingerprint.
- `npm run check:period-close`: 30/30, periodstangning, blockerare, varningar, attest, bankavstamning, momsbevis, sena verifikat, periodstampel och slutlig kedjekod.
- `npm run check:handoff`: 29/29, redovisningspaket, SIE, SIE-kvittens, resultat, balans, huvudbok, saldobalans, moms, bank, reskontra, arsarkiv, systemdokumentation och saker delning.
- `npm run check:go-live-decision`: 17/17, slutligt beslut for lokal MVP kontra skarp drift, externa bevis, backup/restore, Stripe, SMTP, release-sparbarhet och redovisningskonsult-export.
- `npm run check:external-go-live`: 29/29, externa bevis for GitHub sync, Actions, Dockerhub, EC2/RDS, schema, backup/restore, Stripe, SMTP, bank/Swish/kort, AI och redovisningspaket.
- `npm run check:post-push`: 31/31, post-push-verifiering for GitHub sync, Actions, CI-artifacts/summaries, Dockerhub, sparbara image-taggar och strikt efter-push-lage.
- `npm run check:release:full`: passed 2026-08-24, frontend build, runtime smoke, backendtester 204 tests / 0 failures / 0 errors, release gate och Docker image builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-24 12:14 +02:00, AliBooks pre-push gate passed, backendtester 194 tests / 0 failures / 0 errors, runtime smoke, release gate, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-22 21:58 +02:00, AliBooks pre-push gate passed, backendtester, runtime smoke, release gate, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:release:full`: passed 2026-08-17 09:59 +02:00, frontend build, runtime smoke, backendtester och Docker image builds
- `npm run check:release`: passed 2026-08-16 23:37 +02:00, standard gate med schema-migration, schema-bootstrap och git-skydd for `db/`
- `npm run check:dependencies`: passed 2026-08-17 09:54 +02:00, 32/32
- `npm run check:secrets`: passed 2026-08-24, tidigare hemlighetskontroll utan fynd.
- `npm run check:ci-handoff`: 34/34, GitHub Actions-jobb, artifacts/summaries, Dockerhub workflow, secrets, push/sync-steg, vanliga CI-fel och go-live-grans.
- `npm run check:ci`: 39/39, GitHub Actions kontrollerar backend med `mvn -B test`, PostgreSQL 16, Java 21, frontend release gate, Docker image builds, artifacts/summaries, Dependabot och timeout-skydd.
- `npm run check:audit`: passed 2026-08-17 09:54 +02:00, tidigare audit med 0 vulnerabilities
- `npm run check:release-traceability`: passed 2026-08-22, 14/14, warning: local commits pending push
- `npm run test:backend`: passed 2026-08-17 09:58 +02:00, 190 tests, 0 failures, 0 errors
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-09 19:30 +02:00
- `check:ready`: 158/158
- `check:acceptance`: 18/18
- `check:evidence`: 94/94
- `check:bundle`: 9/9
- `check:data-safety`: 57/57
- `check:prod`: 44/44
- `check:env-go-live`: 39/39
- `check:ci`: 39/39
- `check:ci-handoff`: 34/34
- `check:post-push`: 31/31
- `check:external-go-live`: 29/29
- `check:schema`: 18/18
- `check:migrations`: 5/5, passed 2026-08-16
- `check:schema-bootstrap`: 10/10
- `check:go-live-risks`: 20/20
- `check:go-live-decision`: 17/17
- `check:manual-go-live`: 18/18
- `check:mvp-use`: 20/20
- `check:operations`: 23/23
- `check:use-today`: 34/34
- `check:first-real-data`: 34/34
- `check:pilot`: 25/25
- `check:calculations`: 46/46
- `check:retention`: 23/23
- `check:audit-integrity`: 25/25
- `check:period-close`: 30/30
- `check:handoff`: 29/29
- `check:speedledger-parity`: 28/28
- `check:startklar`: 20/20
- `npm run check:backup`: passed, 18/18
- `check:finish-line`: 21/21
- Docker images skapade lokalt 2026-08-24 15:49 +02:00:
  - `alibooks-backend:release-gate`
  - `alibooks-frontend:release-gate`
- CI-konfigurationen kontrolleras lokalt med `npm run check:ci` och ingar i release-gaten. GitHub Actions kor ocksa `npm run check:audit` innan frontend release-gate, sparar `backend-surefire-reports` samt `frontend-dist` som artifacts, och Dependabot bevakar frontend, backend och workflow-beroenden.
- CI-handoff efter push kontrolleras lokalt med `npm run check:ci-handoff` och dokumenteras i [ci-handoff-efter-push.md](ci-handoff-efter-push.md), sa GitHub Actions-jobb, artifacts/summaries, Dockerhub-secrets, push/sync och vanliga CI-fel inte tappas bort.
- Post-push verifiering kontrolleras lokalt med `npm run check:post-push` och strikt efter push med `npm run check:post-push -- --require-pushed`, sa lokal MVP inte blandas ihop med GitHub/CI/Dockerhub-bevis.
- MVP-acceptans kontrolleras lokalt med `npm run check:acceptance` och skiljer automatiskt bevis fran manuella go-live-klicktester.
- Runtime-smoke kontrollerar att utloggad startsida visar kompakt login/register/sprak, och att dessa kontroller inte foljer med till andra menyvyer som Kunder.
- CI kor backendtester med `mvn -B test` och explicit `SPRING_JPA_HIBERNATE_DDL_AUTO=update` for testdatabasen, medan produktion defaultar till `validate` och `APP_SCHEMA_PATCH_ENABLED=false` sa RDS-schema inte andras automatiskt.
- Backup/restore-rutinen kontrolleras lokalt med `npm run check:backup` och ingar i release-gaten.
- Restore drill har skyddade script for Linux/EC2 och Windows som kraver `RESTORE_CONFIRM=RESTORE_TO_TEST_DATABASE`.
- Git release status kan kontrolleras med `npm run check:git` innan commit och `npm run check:git -- --strict` efter commit.
- GitHub sync kan kontrolleras med `npm run check:sync` efter push. Den failar om lokala commits inte finns pa GitHub.
- Go-live-risker foljs i [go-live-riskregister.md](go-live-riskregister.md) och kontrolleras lokalt med `npm run check:go-live-risks`.
- Slutligt go-live-beslut foljs i [go-live-beslut.md](go-live-beslut.md) och kontrolleras lokalt med `npm run check:go-live-decision`.
- Manuella externa go-live-bevis kontrolleras lokalt med `npm run check:manual-go-live`, sa PDF, SMTP, Stripe, bank/betalningsflode, backup/restore, publik URL och redovisningskonsult-export inte tappas bort.
- Anvandningsklar lokal MVP kontrolleras med `npm run check:mvp-use`, som samlar 20 praktiska steg fran lokal start till go-live-beslut.
- Driftberedskap kontrolleras med `npm run check:operations`, sa Driftcenter, incidentlogg, releasejournal, backup/smoke-test och rollback-plan inte tappas bort.
- Sista lokala anvandningsbeslutet kontrolleras med `npm run check:use-today`, sa AliBooks visar nar lokal MVP kan anvandas och nar arbetet ska stoppas innan viktig data registreras.
- Forsta riktiga data kontrolleras med `npm run check:first-real-data`, sa backup, restore drill, testdata, foretagsinstallningar, nummerserier, personuppgifter, betalningsrutin och export ar synliga innan riktiga kunder, fakturor, kvitton eller bankrader registreras.
- Berakningsintegritet kontrolleras med `npm run check:calculations`, sa faktura, moms, delbetalning, Stripe, leverantorer, verifikat, rapporter och lon-MVP inte tappar sina skydd.
- Arkiv och andringsspar kontrolleras med `npm run check:retention`, sa hard delete, kvittoersattning, kundhistorik, leverantorsfakturor, periodlasning och rattelsefloden inte tappar sina skydd.
- Revisionsspar-integritet kontrolleras med `npm run check:audit-integrity`, sa auditkedja, auditstampel, CSV-export, backupkoppling och tamper-kansligt backendtest inte tappar sina skydd.
- Periodstangning kontrolleras med `npm run check:period-close`, sa blockerare, varningar, attest, bankavstamning, momsbevis, sena verifikat, periodstampel och slutlig kedjekod inte tappar sina skydd.
- Redovisningspaket kontrolleras med `npm run check:handoff`, sa SIE, kvittens, huvudbok, saldobalans, rapporter, kontrollbevis, arsarkiv och saker konsultdelning inte tappas bort.
- SpeedLedger-liknande funktionsparitet kontrolleras med `npm run check:speedledger-parity`, sa AliBooks inte overdriver extern bankkoppling, PEPPOL/e-faktura, NE-inlamning, arsredovisning, E-dagsavslut, factoring eller fullservice.
- Pre-push-kontrollen `npm run check:prepush -- --allow-ahead` kor full release gate och ren Git-status innan sjalva pushen. Utan `--allow-ahead` kraver den aven att GitHub redan ar i sync.
- Databasschema-lage kontrolleras med `npm run check:schema` sa `SPRING_JPA_HIBERNATE_DDL_AUTO` och `APP_SCHEMA_PATCH_ENABLED` ar explicita lokalt och produktion defaultar till `validate` plus avstangd startup-patch.
- Kontrollerad schema-migration kontrolleras med `npm run check:migrations`. Filen `db/migrations/001_startup_schema_patch.sql` speglar `DatabaseSchemaPatch` och ska testas mot restore/staging innan RDS-deploy.
- Forsta RDS-basschema kontrolleras med `npm run check:schema-bootstrap` och dokumenteras i [schema-bootstrap-runbook.md](schema-bootstrap-runbook.md).
- Frontend-beroenden och buildverktyg kontrolleras lokalt med `npm run check:dependencies`. Fore skarp deploy ska aven aktuell online-audit koras med `npm run check:audit` eller striktare.
- Release-sparbarhet kontrolleras med `npm run check:release-traceability`: package-version, git branch/commit, Dockerhub `sha-*`/`v*`-taggar och EC2 `IMAGE_TAG`.

## Kvar fore riktig go-live

Detta maste fortfarande verifieras utanfor lokal maskin innan AliBooks anvands som riktig produktionsapp:

- GitHub Actions ska vara gron efter push.
- Efter commit ska `npm run check:git -- --strict` visa att inga viktiga AliBooks-filer ligger kvar utanfor git.
- Efter push ska `npm run check:sync` visa att lokal branch och GitHub ar i sync.
- Dockerhub workflow ska pusha backend/frontend images.
- EC2 ska kora `docker compose -f docker-compose.prod.yml up -d` mot riktig RDS.
- Pa EC2 ska strikt produktionskontroll koras med riktig, ej committad `.env`:

```bash
npm run check:prod -- --env-file ../.env --strict
```

- Produktions-smoke ska visa att publik frontend, backend health och databas fungerar.
- Backup ska vara verifierad innan riktig kunddata och bokforingsdata flyttas in.
- En restore drill ska goras till separat test database innan produktion anvands skarpt.
- RDS-schema ska uppdateras kontrollerat med testad schema-dump plus `db/migrations/001_startup_schema_patch.sql`, inte via automatisk startup-patch i produktion.
