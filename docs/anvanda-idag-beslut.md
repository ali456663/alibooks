# AliBooks anvanda idag - beslut

Det har dokumentet ar den korta beslutspunkten innan AliBooks anvands med riktig arbetsdata i lokal MVP. Det ersatter inte extern produktionsverifiering, men det hjalper dig att veta nar du kan jobba vidare i appen och nar du ska stoppa.

**Nuvarande grans:** lokal utveckling och isolerad testdata ar okej nar lokala kontroller ar grona. Riktiga bokforingsposter ar stoppade tills systemet bevarar kronor och oren och beloppsmigreringen ar verifierad.

## Gront for lokal MVP

AliBooks kan anvandas lokalt for MVP-arbete nar detta ar sant:

- Docker Desktop ar igang och PostgreSQL svarar.
- Backend startar utan `Application failed to start`.
- Frontend visar appen pa `http://localhost:5157` utan vit sida.
- `npm run check:release` ar gron.
- `npm run check:calculations` ar gron.
- `npm run check:first-real-data` visar att skydd finns; ett gront statiskt test ar inte tillstand for riktiga poster.
- `npm run check:pilot` ar gron innan du startar begransad pilotdrift med riktiga kunder.
- `npm run check:git -- --strict` ar gron efter senaste commit.
- `npm run doctor`, `npm run check:views` och `npm run smoke:runtime` ar grona.
- Backup ar kontrollerad innan du importerar eller skapar mycket data.

## Stoppa och fixa forst

Anvand inte AliBooks med viktig data om nagot av detta hander:

- Backendens status visar att fullt stod for kronor och oren saknas. Anvand da bara avskild testdata; for inte in verkliga bokforingsposter.
- AliBooks pengamodell saknar fullt ore-stod: anvand endast isolerad testdata tills migrering och avstamning ar verifierade.
- Frontend visar vit sida eller render recovery.
- Backend far `Connection to localhost:5432 refused`.
- Databasen saknar kolumner eller visar schema drift.
- Verifikat balanserar inte debet och kredit.
- Fakturanummer eller verifikationsnummer verkar hoppa fel.
- Periodlasning blockerar inte gamla datum.
- Backup eller restore drill saknar bevis.
- Riktiga API-nycklar ligger i kod, frontend eller GitHub.

## Daglig rutin

1. Starta Docker Desktop.
2. Kor `docker compose up db`.
3. Starta backend i IntelliJ.
4. Kor `npm run dev` i frontend.
5. Kor `npm run doctor`.
6. Kontrollera `Startklar`, `Driftcenter` och `Sakerhet`.
7. Skapa eller hamta backup fore stor import.
8. Registrera dagens fakturor, betalningar, kvitton och bankrader.
9. Kontrollera `Regelkontroll`, `Redovisningskontroll` och `Momsrapport`.
10. Exportera rapport eller underlag om du ska granska med redovisningskonsult.

## Skarp produktion

Skarp produktion vantar tills externa bevis finns:

- GitHub Actions ar gron efter push.
- Dockerhub images finns med ratt `IMAGE_TAG`.
- EC2 och RDS ar verifierade med produktions-smoke.
- Stripe webhook och SMTP ar testade med riktiga testfloden.
- Backup och restore drill ar testade utanfor produktion.
- Betalningar via kort, Apple Pay, Swish eller kassaregisterkrav ar granskade.
- Slutligt go-live-beslut ar kontrollerat med `npm run check:go-live-decision`.

## Snabb kommando

```bash
npm run check:use-today
npm run check:first-real-data
npm run check:pilot
npm run check:calculations
npm run check:go-live-decision
```

Beslutet ska lasa tillsammans med:

- [anvandningsklar-mvp.md](anvandningsklar-mvp.md)
- [forsta-riktiga-data.md](forsta-riktiga-data.md)
- [pilotdrift-mvp.md](pilotdrift-mvp.md)
- [drift-runbook.md](drift-runbook.md)
- [go-live-riskregister.md](go-live-riskregister.md)
- [go-live-beslut.md](go-live-beslut.md)
