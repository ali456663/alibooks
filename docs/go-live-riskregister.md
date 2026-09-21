# AliBooks go-live riskregister

Det har registret skiljer pa lokal MVP-stabilitet och riktig produktionsklarhet.
Den sista lokala anvandningsgransen kontrolleras med `npm run check:use-today`.
Det slutliga beslutet fore skarp drift finns i [go-live-beslut.md](go-live-beslut.md) och kontrolleras med `npm run check:go-live-decision`.
AliBooks far inte anvandas med skarp kunddata, bokforingsdata eller betalningar innan blockerande externa kontroller ar verifierade.
Det finns aven interna blockerare: fullt ore-stod och en bredare beloppsmodell
ar inte fardiga. Centrala rapporter stoppar nu summor utanfor nuvarande grans;
andra modulers summering aterstar. En gron release-gate godkanner inte skarp bokforing.
Se [beloppssakerhet](money-safety.md) for verifierade rattningar och kvarvarande risker.

## Statusnivaer

- `KLAR LOKALT`: Funktionen ar verifierad pa utvecklingsmaskinen.
- `KRAVER EXTERN VERIFIERING`: Funktionen finns i projektet men maste bevisas i GitHub, Dockerhub, AWS, Stripe, SMTP eller med restore drill.
- `BLOCKERAR SKARP DRIFT`: Riktig drift ska vanta tills punkten ar klar.

## Risker fore skarp drift

Bankimportens nya betalnings- och kostnadsfloden sparar bokforing, historik och
audit atomiskt, med serialiserade aterforsok. Detta minskar risken for dubbla och
halvsparade bokningar men ersatter inte historisk avstamning eller bankens egna
transaktionsidentiteter. Gamla timestamp-ID:n migreras inte automatiskt.
Se [bank-import-atomicity.md](bank-import-atomicity.md).

Bankrader har nu sparad unik journalradskoppling. Aldre okopplade rader och
felmatchningar ger kritiska avstamningsfel aven vid noll nettodifferens.
Manuell koppling till befintlig bokforing ar mojlig efter uttryckligt val,
utan att skapa nya betalningar. Detta loser inte fullstandigheten hos utdrag,
klumpsummor eller ingangsbalanser. Se [bank-journal-links.md](bank-journal-links.md).

| Omrade | Status | Risk | Kontroll | Bevis som kravs |
| --- | --- | --- | --- | --- |
| Momssats och momsklassificering | BLOCKERAR SKARP DRIFT | Nya fakturor och manuella Stripe-forsaljningar kan anvanda 6/12/25 procent per tjanst/forsaljning, men systemet klassificerar inte automatiskt vilken sats som ska anvandas. Kundfakturor med ore i exakt moms stoppas tills minor-unit-migreringen ar klar. | Bekrafta momshanteringen for varje faktisk tjanst med redovisningskonsult och kontrollera faktura, kredit, delbetalning, aterbetalning och momsrapport per sats. Se vat-rates.md och money-safety.md. | Verifierade underlag och bokforingskonton stammer per momssats; historiska poster ar avstamda och beloppens precision ar godkand. |
| Fakturadokument och utskicksatervinning | BLOCKERAR SKARP DRIFT | Nya utstallda fakturor har nu immutable snapshot och original-PDF med kontrollsumma. Aldre dokument kan sakna original. SMTP och databascommit ar inte atomiska. | Bevara originalunderlag, granska rekonstruerade kopior och infor bestaende outbox med hantering av osakra utskick. Se invoice-document-snapshots.md. | Originaldokument kan aterlasas oforandrade och SMTP-timeout/commitfel kan avstammas utan blinda dubbla utskick. |
| Historisk reskontra och bankmatchning | BLOCKERAR SKARP DRIFT | Faktureringsmetodens 1510/2440 kontrolleras per faktura mot huvudboken. Bankkontrollen kraver nu journalradskoppling, inte bara lika totalsummor. Importerad/raderad historik, ingangsbalanser, klumpsummor och kontantmetodens bokslutsflode aterstar. | Verifiera verkliga underlag och ingangsbalanser, migrera leverantorsbetalningar fran text och granska varje aldre bankkoppling. Periodlasning och journalbokforing har gemensamt databaslas. Se bank-journal-links.md, period-write-serialization.md och subledger-control.md. | Verkliga saldon per rapportdatum stammer med underlag/huvudbok; varje bankrad har sparad koppling och dubblettkontroll samt verifierat originalutdrag. |
| Oren och beloppssummering | BLOCKERAR SKARP DRIFT | Heltals-SEK kan inte bevara alla underlagsbelopp; centrala rapporter avvisar for stora summor, ovriga moduler aterstar att granska. | Genomfor versionssatt beloppsmigrering med databas-, API-, rapport- och importtester. | Underlag med oren och stora sammanlagda saldon bevaras exakt hela vagen, inklusive moms och Stripe. |
| GitHub Actions CI | KRAVER EXTERN VERIFIERING | Lokala tester kan vara grona medan GitHub Actions failar efter push. | Pusha release-commits och kontrollera CI. | Backend build and test, Frontend release gate och Docker build ar green i GitHub Actions. |
| GitHub sync | BLOCKERAR SKARP DRIFT | Lokala commits kan saknas pa GitHub, sa moln och demo kor gammal kod. | Kor `npm run check:sync` efter push. | `check:sync` ar gron och branch ar inte ahead of origin/main. |
| Dockerhub images | KRAVER EXTERN VERIFIERING | EC2 kan inte deploya om images inte finns eller har fel tagg. | Kor Dockerhub workflow manuellt eller via versionstagg. | Backend/frontend images finns i Dockerhub med `latest`, `sha-*` eller `v*` tagg. |
| EC2 deploy | BLOCKERAR SKARP DRIFT | Appen kan fungera lokalt men inte som publik URL. | Kor `scripts/ec2-deploy.sh` pa EC2. | Frontend container, backend container och smoke test ar green pa EC2. |
| RDS databas | BLOCKERAR SKARP DRIFT | Backend startar inte eller skriver mot fel databas. | Kor `npm run check:prod -- --env-file ../.env --strict` pa EC2. | `/api/system/status` visar `database.ok = true` mot RDS. |
| Miljo variabler och CORS | BLOCKERAR SKARP DRIFT | Publik frontend kan prata med fel backend, localhost kan ligga kvar, eller svag JWT kan skydda riktig data for daligt. | Kor `npm run check:env-go-live` och `npm run check:prod -- --env-file ../.env --strict`. | `JWT_SECRET` ar stark, `APP_CORS_ALLOWED_ORIGINS` matchar publik frontend, `APP_CORS_LOCAL_DEV_ENABLED=false`, RDS-env ar satt och hemligheter ligger utanfor GitHub. |
| Konto och arbetsyteisolation | BLOCKERAR SKARP DRIFT | Oppen sjalvregistrering ar stangd efter forsta agarkontot och produktionsstartnyckel kravs. Datamodellen saknar fortfarande roller och tenant-isolering; alla autentiserade konton i samma installation ser samma foretagsdata. | Anvand en enda agare for en lokal/enforetags-MVP. Lagg inte till medarbetarkonton eller flera foretag innan tenant- och rollskydd finns. | Forsta kontot ar skapat med startnyckel; registrering nekar fler konton. Separat flerforetags-/rollisolering ar implementerad och testad innan fler anvandare eller foretag tillats. |
| Backup och restore drill | BLOCKERAR SKARP DRIFT | Lokal databas och nio filer aterlastes isolerat 2026-09-09, men nio filer saknar kostnadskoppling och en kostnad saknar kvittoreferens. Skyddad extern kopia och appfloden mot aterlast data ar inte verifierade. | Kor `npm run backup:local` med samtliga skrivare pausade. Granska underlagskopplingar, ordna krypterad separat kopia och prova appens floden i isolerad aterstallningsmiljo. Se backup-restore-runbook.md och release-evidence.md. | Matchande radantal och filhashar finns lokalt. Kompletta underlag, fungerande appaterlasning och aterstallning fran separat lagringsplats aterstar. Enbart `pg_restore -l` ar inte ett aterlasningsprov. |
| Databas-schema och Hibernate | BLOCKERAR SKARP DRIFT | Fel schema kan ge `column does not exist` eller tysta databasandringar om `ddl-auto` eller startup-patchar inte ar valt medvetet. | Kor `npm run check:schema`, `npm run check:migrations` och `npm run check:schema-bootstrap`. Testa schema-dump och `db/migrations/001_startup_schema_patch.sql` mot restore/staging och kontrollera att produktion anvander `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` eller `none` samt `APP_SCHEMA_PATCH_ENABLED=false` fore RDS-deploy. | Schema-lage och schema-patch-flagga ar explicita i `.env`, Docker Compose och production readiness, migrationsfilen speglar backendens startup-SQL, schema-dump ar testad enligt `schema-bootstrap-runbook.md`, och produktion muterar inte RDS-schema automatiskt. |
| Frontend beroenden | KRAVER EXTERN VERIFIERING | Appen kan bygga lokalt men ha stale lockfile, osakra package-specs eller aktuella sarbarheter i npm-ekosystemet. | Kor `npm run check:dependencies` lokalt och `npm run check:audit` med internet fore skarp deploy. | Lockfile/Docker-installation ar gron lokalt och online-audit visar inga hoga eller kritiska produktionsberoenden. |
| Stripe betalningar | KRAVER EXTERN VERIFIERING | Betalningar kan tas emot men inte bokforas korrekt om webhook saknas eller ar fel. | Testa Stripe test-webhook och `checkout.session.completed`. | AliBooks skapar/uppdaterar faktura eller Stripe-forsaljning och bokforingen balanserar. |
| SMTP e-post | KRAVER EXTERN VERIFIERING | Faktura- och paminnelsemail kan se klara ut men inte skickas pa riktigt. | Lagga SMTP i ej committad `.env` och skicka testmail. | Testmail kommer fram och audit/event-historik visar skickad faktura/paminnelse. |
| AI och personuppgifter | BLOCKERAR SKARP DRIFT | Personnummer, adress, telefon eller e-post kan skickas till extern AI av misstag. | Anvand AI-sakert lage och anonymiserad export for analys. | Sakerhetssidan och anonym export visar att PII minimeras eller tas bort. |
| Kort, Swish och kassaregisterregler | KRAVER EXTERN VERIFIERING | Elektroniska betalningar kan krava extra rutin eller kontroll beroende pa saljflode. | Kontrollera med Skatteverket/redovisningskonsult innan skarp kort/Swish/Apple Pay-rutin. | Beslutad rutin ar dokumenterad innan riktig betalningsdrift. |
| Bokforingsansvar | BLOCKERAR SKARP DRIFT | Appen kan rakna ratt i tester men anvandaren ansvarar fortfarande for korrekt bokforing. | Gor manuell rimlighetskontroll och export innan periodlasning/bokslut. | Saldobalans, momsrapport, huvudbok, resultat/balans och arkivexport ar sparade. |

## Minsta go-live-beslut

Innan riktig drift ska dessa vara sant:

1. Lokal full gate ar gron.
2. GitHub Actions ar gron efter push.
3. Dockerhub images finns med vald tagg.
4. EC2/RDS smoke test ar gron.
5. Backup och restore drill ar verifierad.
6. Schema-bootstrap och schema-migration ar testade mot restore/staging och RDS kor med `APP_SCHEMA_PATCH_ENABLED=false`.
7. Stripe och SMTP ar testade om de ska anvandas skarpt.
8. AI anvander minimerad eller anonymiserad data.
9. Betalningsrutinen for kort/Swish/Apple Pay ar kontrollerad.
10. Frontend dependency-audit ar kontrollerad.
11. Release-sparbarhet ar kontrollerad med `npm run check:release-traceability`.
12. En export kan lamnas till redovisningskonsult.

## Snabbt beslut just nu

En gron release-gate visar lokal teknisk teststatus, inte att AliBooks ar klart som enda bokforingssystem.
AliBooks ar inte skarp produktionsklar for riktig kunddata forran punkterna ovan har externt bevis.
