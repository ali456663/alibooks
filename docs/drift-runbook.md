# AliBooks drift-runbook

Den har runbooken ar for lokal MVP och forberedelse mot skarp drift. Den ska anvandas nar AliBooks inte startar, visar vit sida, tappar databaskontakt, far fel i release eller nar du behover dokumentera rollback.

## 1. Snabb triage

Kor fran projektets rotmapp:

```bash
npm run doctor
npm run check:views
npm run smoke:runtime
npm run check:startklar
npm run check:mvp-use
```

Tolka resultatet:

- Om `doctor` failar pa PostgreSQL: starta Docker Desktop och `docker compose up db`.
- Om backend health failar: starta om `CloudShopApplication` i IntelliJ och kontrollera port 3000.
- Om frontend failar: starta om Vite med `npm run dev` och kontrollera `http://localhost:5157`.
- Om appen visar vit sida: kor `npm run smoke:runtime`, oppna med `?reset=1` och anvand knappen for att rensa lokal UI-data.

## 2. Databas och bokforingsdata

Fore riktig kunddata:

```bash
npm run check:backup
npm run check:schema
npm run check:migrations
npm run check:schema-bootstrap
```

Minimikrav:

- backup ska skapas med `pg_dump -Fc`
- backup ska verifieras med `pg_restore -l`
- restore drill ska goras till separat testdatabas
- produktion ska anvanda `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- produktion ska anvanda `APP_SCHEMA_PATCH_ENABLED=false`

Stoppa anvandning om databasfel visar `column does not exist`, `relation does not exist` eller om restore drill inte ar bevisad.

## 3. Incidentlogg

Vid driftstopp eller fel:

1. Oppna `Driftcenter`.
2. Skapa incident med datum, severity, rubrik, paverkan, atgard och ansvarig.
3. Markera incident som lost nar felet ar avhjalpt.
4. Markera incident som verifierad nar smoke test, systemstatus och relevant affarsflode fungerar igen.
5. Exportera incidentloggen som CSV efter storre fel.

Incidenter ska anvandas for:

- vit sida eller render recovery
- backend nere
- databas nere
- misslyckad Stripe/SMTP-test
- fel bokforingsdata eller misstankt felberakning
- restore/backup-problem

## 4. Releasejournal och rollback

Fore varje deploy eller stor lokal release:

1. Kor `npm run check:release`.
2. Vid push/deploy: kor `npm run check:prepush -- --allow-ahead`.
3. Spara release i `Driftcenter` med version, commit, miljo, backupkontroll, smoke test och rollback-plan.
4. Markera release som deployad endast nar backup och smoke test ar grona.
5. Om nagot failar, markera release som rollback och skriv vad som gjordes.

Rollback-planen ska minst innehalla:

- vilken commit eller Dockerhub `IMAGE_TAG` du gar tillbaka till
- vilken backup eller RDS snapshot som skyddar data
- hur du verifierar efter rollback: `/health`, `/system/status`, login, faktura, bokforing och momsrapport

## 5. Produktion

Skarp produktion far inte anvandas med riktig kunddata innan:

- GitHub Actions ar gron efter push
- Dockerhub images finns med ratt tagg
- EC2/RDS smoke test ar gron
- backup och restore drill ar verifierade
- Stripe och SMTP ar testade
- `npm run check:sync` ar gron
- `npm run check:manual-go-live` ar gron

Driftbeslut ska sparas i Driftcenter och kopplas till releasebeviset.

