# AliBooks release evidence

Senast komplett lokalt releasebevis: 2026-08-24 12:14 +02:00.
Senast standard-release och backendtest verifierat: 2026-08-24 15:08 +02:00.

## Kommandon som ska vara grona fore push/deploy

Kor fran projektets rotmapp:

```bash
npm run check:release:full
npm run check:secrets
npm run check:dependencies
npm run check:ci-handoff
npm run check:startklar
npm run check:mvp-use
npm run check:operations
npm run check:use-today
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
- frontend smoke-test kan rendera appen och kontrollerar att utloggad auth/sprak bara syns pa startsidan
- viktiga frontend/backend API-kontrakt finns kvar
- backend-konstruktorer och Java-records matchar testerna
- dokumentation, secrets, Docker-konfig, vyer och produktionsmallar passerar kontroller
- riktiga API-nycklar for Stripe, HF, Google, OpenRouter och OpenAI-liknande providers stoppas av `check:secrets`
- frontend dependency-lockfile, buildverktyg och Docker-installation kontrolleras statiskt
- CI-handoff efter push ar dokumenterad med GitHub Actions-jobb, Dockerhub-secrets, jobbnamn och felsokning
- release-version, commit, Dockerhub-taggar och EC2 `IMAGE_TAG` gar att sparas som releasebevis
- startup-schema-patchar ar speglade i kontrollerad SQL-migration innan RDS-deploy
- forsta RDS-basschema har en kontrollerad schema-bootstrap-runbook
- destruktiva raderingar/reset-endpoints har JWT, feature flags, audit och periodlasningsskydd dar det kravs
- revisionsspar har SHA-256-kedja, auditstampel, CSV-export, backupkoppling och backendtest mot andrad historik
- periodstangning har backendkontroll for blockerare, varningar, attest, bankavstamning, momsbevis, periodstampel och slutlig kedjekod
- redovisningspaket kan exportera SIE, kvittenser, huvudbok, saldobalans, rapporter, kontroller och arsarkiv for saker konsultoverlamning
- slutligt go-live-beslut skiljer lokal MVP fran skarp drift och kraver externa bevis innan riktig kunddata
- externa go-live-bevis for GitHub sync, Actions, Dockerhub, EC2/RDS, schema, backup/restore, Stripe, SMTP, bank/Swish/kort, AI och redovisningspaket ar dokumenterade
- backendtester passerar
- backend Docker-image kan byggas
- frontend Docker-image kan byggas

## Senaste lokala bevis

- Rotkommandon verifierade 2026-08-22 21:43 +02:00: `npm run build`, `npm run check:docs`, `npm run check:release` och `npm run test:backend` fungerar fran projektets huvudmapp.
- `npm run test:backend`: passed 2026-08-24, 201 tests, 0 failures, 0 errors
- `npm run check:release`: passed 2026-08-24, standard gate fran projektroten med 18/18 acceptans, 135/135 readiness, 70/70 evidence, 55/55 data safety, 44/44 production readiness, 23/23 retention, 25/25 audit-integritet, 30/30 periodstangning, 29/29 redovisningspaket, 17/17 go-live-beslut, 29/29 externa go-live-bevis och runtime smoke
- `npm run check:startklar`: passed 2026-08-24, 20/20, lokal MVP redo enligt kort Startklar-kontroll. Skarp produktion vantar pa GitHub sync, Dockerhub, EC2/RDS, restore drill, Stripe och SMTP.
- `npm run check:mvp-use`: 20/20, 20-stegs kontroll for anvandningsklar lokal MVP.
- `npm run check:operations`: 23/23, drift-runbook, incidentlogg, releasejournal och rollback-kontroll.
- `npm run check:use-today`: 30/30, slutligt lokalt anvandningsbeslut med stoppsignaler och produktionsblockerare.
- `npm run check:calculations`: 46/46, berakningsintegritet for faktura, moms, delbetalning, Stripe, leverantorer, verifikat och lon-MVP.
- `npm run check:retention`: 23/23, arkiv och andringsspar for fakturor, kunder, leverantorsfakturor, kvitton, bankreset, periodlasning, hard delete, rattelser och backendtester.
- `npm run check:audit-integrity`: 25/25, revisionsspar, SHA-256-kedja, auditstampel, CSV-export, backupkoppling, JWT-krav och backendtester som visar att andrad historik ger ny fingerprint.
- `npm run check:period-close`: 30/30, periodstangning, blockerare, varningar, attest, bankavstamning, momsbevis, sena verifikat, periodstampel och slutlig kedjekod.
- `npm run check:handoff`: 29/29, redovisningspaket, SIE, SIE-kvittens, resultat, balans, huvudbok, saldobalans, moms, bank, reskontra, arsarkiv, systemdokumentation och saker delning.
- `npm run check:go-live-decision`: 17/17, slutligt beslut for lokal MVP kontra skarp drift, externa bevis, backup/restore, Stripe, SMTP, release-sparbarhet och redovisningskonsult-export.
- `npm run check:external-go-live`: 29/29, externa bevis for GitHub sync, Actions, Dockerhub, EC2/RDS, schema, backup/restore, Stripe, SMTP, bank/Swish/kort, AI och redovisningspaket.
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-24 12:14 +02:00, AliBooks pre-push gate passed, backendtester 194 tests / 0 failures / 0 errors, runtime smoke, release gate, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-22 21:58 +02:00, AliBooks pre-push gate passed, backendtester, runtime smoke, release gate, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:release:full`: passed 2026-08-17 09:59 +02:00, frontend build, runtime smoke, backendtester och Docker image builds
- `npm run check:release`: passed 2026-08-16 23:37 +02:00, standard gate med schema-migration, schema-bootstrap och git-skydd for `db/`
- `npm run check:dependencies`: passed 2026-08-17 09:54 +02:00, 32/32
- `npm run check:secrets`: passed 2026-08-24, inga riktiga Stripe-, HF-, Google-, OpenRouter-, OpenAI-liknande, GitHub-, JWT- eller private-key-hemligheter hittades i tracked project files.
- `npm run check:ci-handoff`: 32/32, GitHub Actions-jobb, Dockerhub workflow, secrets, push/sync-steg, vanliga CI-fel och go-live-grans.
- `npm run check:audit`: passed 2026-08-17 09:54 +02:00, 0 vulnerabilities
- `npm run check:release-traceability`: passed 2026-08-22, 14/14, warning: local commits pending push
- `npm run test:backend`: passed 2026-08-17 09:58 +02:00, 190 tests, 0 failures, 0 errors
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-09 19:30 +02:00
- `check:ready`: 135/135
- `check:acceptance`: 18/18
- `check:evidence`: 70/70
- `check:data-safety`: 55/55
- `check:prod`: 44/44
- `check:ci`: 29/29
- `check:ci-handoff`: 32/32
- `check:external-go-live`: 29/29
- `check:schema`: 18/18
- `check:migrations`: 5/5, passed 2026-08-16
- `check:schema-bootstrap`: 10/10
- `check:go-live-risks`: 20/20
- `check:go-live-decision`: 17/17
- `check:manual-go-live`: 18/18
- `check:mvp-use`: 20/20
- `check:operations`: 23/23
- `check:use-today`: 30/30
- `check:calculations`: 46/46
- `check:retention`: 23/23
- `check:audit-integrity`: 25/25
- `check:period-close`: 30/30
- `check:handoff`: 29/29
- `check:speedledger-parity`: 28/28
- `check:startklar`: 20/20
- `npm run check:backup`: passed, 18/18
- Docker images skapade lokalt 2026-08-17 09:59 +02:00:
  - `alibooks-backend:release-gate`
  - `alibooks-frontend:release-gate`
- CI-konfigurationen kontrolleras lokalt med `npm run check:ci` och ingar i release-gaten. GitHub Actions kor ocksa `npm run check:audit` innan frontend release-gate.
- CI-handoff efter push kontrolleras lokalt med `npm run check:ci-handoff` och dokumenteras i [ci-handoff-efter-push.md](ci-handoff-efter-push.md), sa GitHub Actions-jobb, Dockerhub-secrets, push/sync och vanliga CI-fel inte tappas bort.
- MVP-acceptans kontrolleras lokalt med `npm run check:acceptance` och skiljer automatiskt bevis fran manuella go-live-klicktester.
- Runtime-smoke kontrollerar att utloggad startsida visar kompakt login/register/sprak, och att dessa kontroller inte foljer med till andra menyvyer som Kunder.
- CI kor backendtester med explicit `SPRING_JPA_HIBERNATE_DDL_AUTO=update` for testdatabasen, medan produktion defaultar till `validate` och `APP_SCHEMA_PATCH_ENABLED=false` sa RDS-schema inte andras automatiskt.
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
