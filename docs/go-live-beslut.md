# AliBooks go-live-beslut

Det har ar den sista gransen mellan Lokal MVP och Skarp drift.

AliBooks kan vara starkt lokalt utan att vara klar for riktig kunddata. Skillnaden ar externa bevis: GitHub Actions, Dockerhub, EC2, RDS, backup, restore drill, Stripe, SMTP och release-sparbarhet.

## Beslut

| Lage | Beslut | Villkor |
| --- | --- | --- |
| Lokal utveckling | GO lokalt | `npm run check:release:full`, backendtester, Docker-build och runtime smoke ar grona. |
| Forsiktig MVP-test | GO lokalt | Testdata anvands, backup finns och inga viktiga filer ar ocommitade. |
| Skarp drift | NO-GO skarp drift | Externa bevis saknas fortfarande for GitHub Actions, Dockerhub, EC2/RDS, backup/restore, Stripe eller SMTP. |
| Riktig kunddata | BLOCKERAR | AliBooks ska inte anvandas med riktig kunddata innan externa go-live-bevis ar sparade. |

## Externa bevis som maste finnas

1. GitHub Actions visar gron backend build/test, frontend release gate och Docker build.
2. `npm run check:sync` ar gron efter push, sa lokal branch inte ligger fore `origin/main`.
3. Dockerhub har backend/frontend images med vald `sha-*` eller `v*` tagg.
4. EC2 kor ratt image och publik frontend fungerar.
5. RDS anvands av backend och `/api/system/status` visar att databasen ar OK.
6. Backupfil ar skapad och kontrollerad.
7. Restore drill ar kord till separat testdatabas, inte produktion.
8. Stripe test-webhook bokfor en betalning korrekt om Stripe ska anvandas skarpt.
9. SMTP skickar testmail och faktura/paminnelsemail nar e-post ska anvandas skarpt.
10. `npm run check:release-traceability` visar version, commit, Docker-taggar och EC2 `IMAGE_TAG`.
11. SIE, huvudbok, saldobalans, resultat, balans och momsrapport kan lamnas till redovisningskonsult.

## Kommandoordning

Kor fran projektets rotmapp:

```bash
npm run check:release:full
npm run check:git -- --strict
git push
npm run check:sync
npm run check:prepush -- --allow-ahead
npm run check:go-live-decision
```

Efter push ska GitHub Actions kontrolleras i webblasaren.

## Om nagot gar fel

- Om appen visar vit sida: stoppa arbete med riktig data och kor runtime smoke.
- Om backend inte startar: kontrollera Docker/PostgreSQL, `.env` och schema-lage.
- Om deploy misslyckas: gor rollback till senast kanda fungerande Docker-tagg.
- Om databasen ar fel: anvand backup och restore till separat testdatabas forst.
- Om bokforing eller moms verkar fel: exportera SIE, huvudbok och saldobalans till redovisningskonsult innan periodlasning.

## Kort slutsats

AliBooks ar nara anvandningsklar lokal MVP nar release-gaten ar gron.
AliBooks ar inte skarp driftklar for riktig kunddata forran externa bevis ovan ar verifierade och sparade.
