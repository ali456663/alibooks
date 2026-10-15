# Fakturadokument och utskicksordning

## Omfattning 2026-09-18

Nya vanliga fakturor och avtalsfakturor sparar en versionssatt kopia av kundens
kontaktuppgifter, tjanstens namn, utstallarens registrerade adress,
organisationsnummer och momsregistreringsnummer, kontaktadress, F-skatt och
betalningsuppgifter. Fakturans befintliga beloppsfalt anvands fortsatt.
Nar ett utkast utfardas uppdateras endast utstallarens profiluppgifter fran
foretagsinstallningarna. Kund, tjanst och betalningsuppgifter forblir frysta.
Efter utfardande skriver senare registerandringar inte om PDF-kopian.
Kreditfakturan arver originalets sparade dokumentuppgifter och belopp.
Snapshotversionerna 1 och 2 kan fortsatt lasas; nya och uppdaterade utkast
sparas som version 3. Endast den sarskilda utkastmetoden far uppdatera
utstallarprofilen.

Utfardande och mejlutskick stoppas innan bokforing om foretagsnamn eller
registrerad adress saknas. Momsregistreringsnummer kravs nar fakturan har ett
momsbelopp. Tjanstebeskrivning kravs. Nar totalbeloppet overstiger 4 000 SEK
inklusive moms kravs ocksa koparens namn och fullstandiga adress innan fakturan
kan utfardas. PDF:en visar enhetspris exklusive moms och beskattningsunderlag.
Kreditfakturan hanvisar till originalets synliga fakturanummer, inte dess interna
databas-ID. Detta ar grundkontroller, inte en fullstandig kontroll av
fakturatext, momsbehandling eller att uppgifterna ar verifierade mot Skatteverket.
Faktureringsregler och grans for forenklad faktura: [Skatteverket](https://www.skatteverket.se/foretagochorganisationer/moms/saljavarorochtjanster/fakturering.4.58d555751259e4d66168000403.html).

PDF visar betalt och kvarvarande belopp separat. Ett utkast markeras som utkast,
inte som en betalningsbegaran med noll kvar. Kreditfakturan har negativ total,
originalreferens och ingen betalningsbegaran. Fakturor utan kundregisterkoppling
visar det sparade kundnamnet.

## Aldre fakturor och arkivering

Aldre fakturor med NULL document_snapshot behaller NULL vid lasning. PDF-kopian
markeras uttryckligen som rekonstruerad och maste jamforas med originalet.
Vi hittar inte pa historiska uppgifter genom automatisk backfill. Trasig eller
okand snapshotversion stoppar renderingen i stallet for tyst registerfallback.
Ett aldre outstallt utkast kan fa snapshot nar det uttryckligen skickas.

Original-PDF:en for nya utstallda fakturor sparas separat som bytea med SHA-256
och kan inte ersattas. Betalningsstatus och restsaldot i fakturapostens levande
vy uppdateras fortfarande, medan PDF-endpointen och mejlbilagan anvander den
sparade originalfilen. Bevara anda originalunderlag enligt ordinarie arkivrutin.
Databasen avvisar nu direkt UPDATE och DELETE av `invoice_originals` via
`invoice_originals_immutable`. Startverifieringen kraver att triggern ar aktiv,
och en isolerad backup-aterlasning underkanns om den saknas eller ar avstangd.
Detta ersatter inte skyddade backupkopior eller granskning av privilegierade
databasadministrators atkomst. Ett redan skadat original avvisas vid lasning.
Historiska registeruppgifter eller PDF-bytes for aldre fakturor som redan saknas
kan inte atervinnas av funktionen.
Mejlutskick kraver nu ett verifierat arkiverat original. En rekonstruerad
historisk PDF kan granskas i lasvyn men skickas inte automatiskt som original;
utred och dokumentera saknat underlag innan vidare hantering.

## Migration och integritet

Migrationen lagger till nullable TEXT-kolumnen customer_orders.document_snapshot.
Startup-patch och db/migrations/001_startup_schema_patch.sql innehaller samma SQL.
Ingen konvertering av gamla belopp eller retroaktiv dokumentuppdatering gors.
Prova migrationen mot isolerad aterlast databas fore produktion enligt ordinarie
schema-runbook. Den har andringen har inte migrerat anvandarens riktiga databas.

Snapshot och original-PDF innehaller personuppgifter. De foljer med PostgreSQL-
backupen och ska skyddas som originalfakturan. De ar avsiktligt utelamnade ur den generella
Order-JSON-responsen; endast documentSnapshotAvailable exponeras dar.
En vanlig JSON-export ar darfor inte en komplett backup av fakturadokumenten.
Detta innebar inte att andra API-falt eller exporter automatiskt ar anonymiserade.

## Avtal och e-post

Avtalsfaktura, dokumentuppgifter, nasta fakturadatum och audit sparas i samma
transaktion med radlas pa avtalet. Fel rullar tillbaka hela operationen.
Separata upprepade anvandarklick ar fortfarande separata fakturabegaran;
detta ar inte ett idempotensprotokoll for avtal.

Vid vanligt fakturamejl lases fakturan. Ett utkast kontrolleras mot periodlas,
bokfors, far status SENT och flushas innan SMTP anropas. Audit och historik
ingar i transaktionen. Redan utstalld faktura eller kredit bokfors inte igen
vid nytt utskick. Fel i kontroller, bokforing eller audit stoppar SMTP-anropet.
Ett SMTP-undantag rullar tillbaka databasandringarna.

Kvarvarande risk: SMTP-servern kan ha accepterat mejlet innan ett timeoutfel,
eller databascommit kan misslyckas efter lyckat SMTP-anrop. Kunden kan da ha
fatt mejlet utan motsvarande sparad utskicksstatus. Automatisk retry far inte
antas vara dubblettfri. En bestaende outbox med avstamning av osakra utfall
behovs for ett robust produktionsflode. Originalarkivet loser inte denna
distribuerade commit-risk. Fakturamejl skickas fortfarande till
kundregistrets aktuella e-postadress; snapshoten styr PDF-uppgifterna.
Paminnelseflodet omfattas inte av denna transaktionsandring.

Manuella paminnelser kraver en utstalld faktura med forfallodatum och obetalt
saldo. Ett tidigare PENDING eller UNCERTAIN leveransforsok av samma
paminnelsetyp blockerar omsandning tills utfallet har avstamts manuellt.

Autentiserad operator kan lista hogst 100 olosta forsok med
`GET /email-delivery/unresolved`. Jamfor forsokets mottagare, rubrik,
tidpunkt och faktura med SMTP-leveranslogg och mottagarbekraftelse. Avstam
ett forsok med `POST /email-delivery/{attemptId}/resolve` och JSON
`{"outcome":"DELIVERED","evidence":"Extern leveransbekraftelse och referens ..."}`
eller `NOT_DELIVERED` med motsvarande bevis. Underlaget maste vara 20-500
tecken och hamnar i revisionsloggen; lagg inte hemligheter dar. Atgarden
skickar inte ett mejl. `DELIVERED` aterstaller saknad skickathistorik,
`NOT_DELIVERED` tillater nytt manuellt fakturamejlforsok endast om samtliga
tidigare forsok ar bekraftat ej levererade. Automatiska paminnelser forsoker
inte igen efter ett leveransforsok. Kan utfallet inte bevisas ska forsoket
fortsatta vara olost och omsandning stoppas. Denna kontroll loser inte den
icke-atomiska SMTP/databas-commit-risken eller ersatter ett verkligt
leveransprov i skarp miljo.

## Verifiering

InvoiceDocumentTest provar snapshot, registerandringar, kredit, utkast,
delbetalningar, legacy-fallback och konvertering. CloudShopApplicationIT provar
PostgreSQL-lagring, avtalsrollback, exportgrans och SMTP-ordning med mockad
transport. Testerna skickar inte riktig e-post. Syntetiska PDF-prov skapas i
backend/target/pdf-proof och granskas visuellt; de innehaller inte kunddata.
Se release-evidence.md for korresultat och kvarvarande driftblockerare.
