# AliBooks release evidence

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
