# AliBooks post-push verifiering

Det har dokumentet anvands direkt efter `git push`.

Syftet ar att skilja tre saker:

1. Lokal kod ar gron.
2. GitHub har samma kod.
3. CI, Dockerhub och externa go-live-bevis ar kontrollerade.

AliBooks far inte behandlas som skarp produktionsklar bara for att lokal release-gate ar gron.

## Kort kommandoordning

Kor fran projektets rotmapp:

```bash
npm run check:release:full
npm run check:git -- --strict
git push
npm run check:sync
npm run check:post-push -- --require-pushed
```

`npm run check:post-push` utan `-- --require-pushed` kontrollerar checklistan och workflows lokalt.
`npm run check:post-push -- --require-pushed` anvands efter push och stoppar om lokal branch fortfarande ligger fore GitHub.

## GitHub Actions

Oppna:

```text
https://github.com/ali456663/alibooks/actions
```

Kontrollera senaste commit och workflow `CI`.

Alla dessa jobb ska vara grona:

- `Backend build and test`
- `Frontend release gate`
- `Docker build`

Om ett jobb ar rott ska du inte deploya.

Kontrollera ocksa bevisen i samma workflow:

- jobbsummaries ska visa AliBooks backend/frontend/Docker-bevis
- artifact `backend-surefire-reports` ska finnas for backendens Maven-testloggar
- artifact `frontend-dist` ska finnas for byggd frontendbundle

## Dockerhub

Dockerhub workflow ska kunna koras manuellt eller via versionstagg.
Publicering sker forst efter en gron releasevalidering med CI-kontrakt,
rapporttester och frontendens release gate. EC2-deployen vantar pa backend- och
frontend-healthchecks innan publikt smoke-test kors.

Krav:

- GitHub secret `DOCKERHUB_USERNAME` finns.
- GitHub secret `DOCKERHUB_TOKEN` finns.
- Backend image publiceras som `cloudshop-backend`.
- Frontend image publiceras som `cloudshop-frontend`.
- Image tagg ar sparbar: `sha-*` eller `v*`.
- EC2 ska anvanda en sparbar `IMAGE_TAG`, inte bara blind `latest`.

## Efter push men fore skarp drift

Kontrollera ocksa:

- `npm run check:release-traceability`
- `npm run check:go-live-decision`
- `npm run check:external-go-live`

Skarp drift vantar fortfarande pa:

- publik frontend URL
- publik backend/API URL
- EC2 status
- RDS status
- backupfil
- restore drill till separat testdatabas
- Stripe test-webhook
- SMTP testmail
- redovisningspaket till konsult

## Vanliga stopp

- `npm run check:sync` failar: lokala commits finns inte pa GitHub.
- GitHub Actions visar fel commit: vanta eller pusha ratt branch.
- Backendtest failar i CI: kontrollera Java 21, PostgreSQL 16 och controller/test-signaturer.
- Frontend release gate failar: kor samma kommando lokalt och fixa forsta `FAIL`.
- Docker build failar: kontrollera Dockerfile, lockfile och att filer ar commitade.
- CI artifacts saknas: kontrollera `actions/upload-artifact@v4`, `backend-surefire-reports`, `frontend-dist` och jobbsummaries.
- Dockerhub saknar tagg: kontrollera secrets och workflow `Dockerhub`.

## Beslut

Post-push verifiering ar gron nar lokal branch ar synkad, GitHub Actions ar gron och Dockerhub/externa bevis ar kontrollerade.

Om nagot av detta saknas ar AliBooks fortfarande lokal MVP, inte skarp drift.
