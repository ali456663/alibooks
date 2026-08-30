# AliBooks MVP-slutspurt

Den har checklistan ar sista raka vagen fran stark lokal MVP till trygg forsta anvandning. Den ersatter inte redovisningskonsult, Skatteverket-kontroll eller extern go-live, men den gor kvarvarande arbete konkret och kontrollerbart.

## Princip

- Lokalt gront betyder att appen kan testas och anvandas forsiktigt med kontrollerad data.
- Skarp drift kraver externa bevis: GitHub Actions, Dockerhub, EC2/RDS, backup/restore, Stripe, SMTP och betalningsrutin.
- Riktig bokforing ska alltid kunna exporteras, granskas och rattas utan att historik skrivs over.

## 20 steg kvar till anvandningsklar MVP

| Steg | Omrade | Mal | Bevis |
| --- | --- | --- | --- |
| 01 | Git status | Inga viktiga filer ligger ostagade eller ocommitade. | `npm run check:git -- --strict` |
| 02 | GitHub sync | Lokal kod finns pa GitHub innan CI och moln bevisar nagot. | `git push`, sedan `npm run check:sync` |
| 03 | GitHub Actions | Backend, frontend och Docker build ar grona i GitHub. | Actions-run med Backend build and test, Frontend build och Docker build |
| 04 | Dockerhub | Backend och frontend images finns med sparbar tagg. | `latest`, `sha-*` eller `v*` tagg i Dockerhub |
| 05 | Lokal start | Docker Desktop, PostgreSQL, backend och frontend startar utan vit sida. | `npm run doctor`, `npm run smoke:runtime` |
| 06 | Systemstatus | Backend, databas, JWT, CORS, Stripe och SMTP visas tydligt. | `Installningar > Systemstatus` |
| 07 | Foretagsinstallningar | Foretagsform, bokforingsmetod, momsperiod, F-skatt och betalinfo ar kontrollerade. | `Installningar` och `Startklar` |
| 08 | Testdata | Gammal demo/testdata ar rensad innan riktig data blandas in. | `Forsta riktiga data` |
| 09 | Kundflode | Skapa kund med riktiga valideringsfel for namn, e-post, personnummer och adress. | Manuell klickkontroll |
| 10 | Fakturaflode | Skapa faktura, PDF, skickad status och betalning/delbetalning fungerar. | `MVP-testprotokoll` |
| 11 | Bokforing | Verifikat balanserar och faktura/kostnad/Stripe skapar ratt rader. | `npm run check:calculations` |
| 12 | Periodlasning | Lasta perioder stoppar sena andringar och styr rattelser till ny period. | `npm run check:period-close` |
| 13 | Underlag | Kvitton, fakturor och PDF-underlag ar sparade och sokbara. | `Underlag` + manuell export |
| 14 | Bank-CSV | Bankrader kan importeras, matchas, hoppas over och exporteras. | `Betalningar` / bankavstamning |
| 15 | Stripe MVP | Manuell Stripe-forsaljning och utbetalning kan bokforas utan dubbelbokning. | Stripe testdata + `1580` avstamning |
| 16 | E-post MVP | Faktura- och paminnelsemail skickas med SMTP-test och sparad historik. | Testmail + audit/eventhistorik |
| 17 | Moms | Momsrapport, momsavrakning och betalning till skattekonto ar granskningsbara. | `Momsrapport`, `check:go-live-risks` |
| 18 | Rapporter | Resultat, balans, huvudbok, saldobalans och reskontra gar att exportera. | `npm run check:handoff` |
| 19 | Backup/restore | Backup ar skapad och restore drill ar testad mot separat testdatabas. | `npm run check:backup` + restorelogg |
| 20 | Go-live beslut | Lokal MVP, pilot och skarp drift ar tydligt separerade. | `npm run check:use-today`, `npm run check:external-go-live` |

## Stoppregler

Stoppa och fixa innan riktig anvandning om:

- `npm run check:release` failar,
- backend inte kan ansluta till PostgreSQL,
- frontend visar vit sida eller render recovery,
- verifikat inte balanserar,
- periodlasning tillater gamla andringar,
- backup saknas eller restore drill inte ar testad,
- riktiga API-nycklar syns i GitHub,
- GitHub Actions eller Dockerhub inte har bevisat senaste commit,
- Stripe/SMTP anvands skarpt utan testbevis.

## Snabbaste vagen just nu

1. Kor `npm run check:release:full`.
2. Kor `npm run check:git -- --strict`.
3. Pusha med `git push`.
4. Kor `npm run check:sync`.
5. Kontrollera GitHub Actions.
6. Kontrollera Dockerhub images.
7. Starta publik demo pa EC2/RDS.
8. Kor produktions-smoke.
9. Testa ett riktigt MVP-flode med en testkund.
10. Ta backup och restore-drill innan riktig kunddata.
