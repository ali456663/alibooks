# AliBooks release evidence

Senast lokal full gate: 2026-08-09.

## Kommandon som ska vara grona fore push/deploy

Kor fran `frontend`:

```bash
npm run check:release -- --with-backend --with-docker-build
npm run check:git
npm run check:sync
npm run check:prepush -- --allow-ahead
```

Detta bevisar lokalt att:

- frontend bygger for produktion
- frontend smoke-test kan rendera appen
- viktiga frontend/backend API-kontrakt finns kvar
- backend-konstruktorer och Java-records matchar testerna
- dokumentation, secrets, Docker-konfig, vyer och produktionsmallar passerar kontroller
- destruktiva raderingar/reset-endpoints har JWT, feature flags, audit och periodlasningsskydd dar det kravs
- backendtester passerar
- backend Docker-image kan byggas
- frontend Docker-image kan byggas

## Senaste lokala bevis

- `npm run check:release -- --with-backend --with-docker-build`: passed 2026-08-09 18:23 +02:00
- `npm run check:release`: passed 2026-08-09 18:48 +02:00
- `npm run test:backend`: passed 2026-08-09 18:49 +02:00, 184 tests, 0 failures, 0 errors
- `check:ready`: 59/59
- `check:data-safety`: 55/55
- `check:prod`: 39/39
- `npm run check:backup`: passed
- Docker images skapade lokalt:
  - `alibooks-backend:release-gate`
  - `alibooks-frontend:release-gate`
- CI-konfigurationen kontrolleras lokalt med `npm run check:ci` och ingar i release-gaten.
- Backup/restore-rutinen kontrolleras lokalt med `npm run check:backup` och ingar i release-gaten.
- Git release status kan kontrolleras med `npm run check:git` innan commit och `npm run check:git -- --strict` efter commit.
- GitHub sync kan kontrolleras med `npm run check:sync` efter push. Den failar om lokala commits inte finns pa GitHub.
- Go-live-risker foljs i [go-live-riskregister.md](go-live-riskregister.md) och kontrolleras lokalt med `npm run check:go-live-risks`.
- Pre-push-kontrollen `npm run check:prepush -- --allow-ahead` kor full release gate och ren Git-status innan sjalva pushen. Utan `--allow-ahead` kraver den aven att GitHub redan ar i sync.
- Databasschema-lage kontrolleras med `npm run check:schema` sa `SPRING_JPA_HIBERNATE_DDL_AUTO` ar explicit lokalt och i produktion.

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
