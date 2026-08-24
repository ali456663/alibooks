# Externa go-live-bevis

Det har dokumentet ar AliBooks sista grind mellan lokal MVP och riktig drift.

Lokal MVP betyder att appen fungerar pa utvecklingsdatorn. Skarp drift betyder att appen far hantera riktig kunddata, bokforingsdata, betalningar, kvitton och e-post i molnet. De tva lagen far inte blandas ihop.

## Obligatoriska bevis fore skarp drift

| Omrade | Bevis | Stoppsignal |
| --- | --- | --- |
| GitHub sync | `npm run check:sync` ar gron efter push och branch ar inte ahead of `origin/main`. | Lokala commits saknas pa GitHub. |
| GitHub Actions | Backend build/test, frontend release gate och Docker build ar grona i Actions. | CI visar rött eller saknar senaste commit. |
| Dockerhub | Backend och frontend finns med vald `IMAGE_TAG`, helst `sha-*` eller `v*`. | EC2 kor `latest` utan sparbar version. |
| EC2 publik app | Publik frontend-URL fungerar och `/api/system/status` svarar. | Appen fungerar bara pa localhost. |
| RDS PostgreSQL | Backend status visar `database.ok = true` mot RDS. | Backend skriver mot lokal Docker-databas i produktion. |
| Schema | RDS ar bootstrapad via granskad schemafil och produktion kor `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` samt `APP_SCHEMA_PATCH_ENABLED=false`. | Hibernate skapar/andrar produktionsschema automatiskt. |
| Backup | `pg_dump` finns, `pg_restore -l` passerar och restore drill ar testad i separat databas. | Backup finns men aterlasning har aldrig testats. |
| Stripe | Test-webhook for `checkout.session.completed` ar verifierad och bokforingen balanserar. | Betalning tas emot men AliBooks far ingen saker signal. |
| SMTP | Testmail, faktura och paminnelsemail kommer fram med sparad audit-historik. | UI sager skickat men mailserver ar inte verifierad. |
| Bank/Swish/kort | Rutinen for kort, Apple Pay, Swish och bankbetalningar ar kontrollerad med redovisningskonsult eller relevant regelstod. | Betalflode kan krava annan rutin an faktura/Stripe-avstamning. |
| AI och data | AI-sakert lage anvands och extern AI far bara minimerad eller anonymiserad data. | Personnummer, adress, telefon eller e-post skickas till extern AI. |
| Redovisningspaket | SIE, huvudbok, saldobalans, resultat, balans, momsrapport och arsarkiv kan exporteras till konsult. | Data ar last i appen utan granskningsbar export. |

## Kommandoordning

Kor fran projektets rotmapp:

```bash
npm run check:release:full
npm run check:external-go-live
npm run check:git -- --strict
git push
npm run check:sync
npm run check:post-push -- --require-pushed
```

Efter push ska GitHub Actions och Dockerhub granskas manuellt. Nar EC2/RDS finns ska produktionskontrollen koras pa EC2:

```bash
npm run check:prod -- --env-file ../.env --strict
```

## Beslut

AliBooks kan anvandas som lokal MVP nar release-gaten ar gron och du har testat huvudflodet manuellt.

AliBooks ska inte anvandas med skarp kunddata innan samtliga externa bevis ovan ar klara eller tydligt accepterade som risker.
