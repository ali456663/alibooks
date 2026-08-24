# AliBooks go-live riskregister

Det har registret skiljer pa lokal MVP-stabilitet och riktig produktionsklarhet.
Den sista lokala anvandningsgransen kontrolleras med `npm run check:use-today`.
AliBooks far inte anvandas med skarp kunddata, bokforingsdata eller betalningar innan blockerande externa kontroller ar verifierade.

## Statusnivaer

- `KLAR LOKALT`: Funktionen ar verifierad pa utvecklingsmaskinen.
- `KRAVER EXTERN VERIFIERING`: Funktionen finns i projektet men maste bevisas i GitHub, Dockerhub, AWS, Stripe, SMTP eller med restore drill.
- `BLOCKERAR SKARP DRIFT`: Riktig drift ska vanta tills punkten ar klar.

## Risker fore skarp drift

| Omrade | Status | Risk | Kontroll | Bevis som kravs |
| --- | --- | --- | --- | --- |
| GitHub Actions CI | KRAVER EXTERN VERIFIERING | Lokala tester kan vara grona medan GitHub Actions failar efter push. | Pusha release-commits och kontrollera CI. | Backend build and test, Frontend release gate och Docker build ar green i GitHub Actions. |
| GitHub sync | BLOCKERAR SKARP DRIFT | Lokala commits kan saknas pa GitHub, sa moln och demo kor gammal kod. | Kor `npm run check:sync` efter push. | `check:sync` ar gron och branch ar inte ahead of origin/main. |
| Dockerhub images | KRAVER EXTERN VERIFIERING | EC2 kan inte deploya om images inte finns eller har fel tagg. | Kor Dockerhub workflow manuellt eller via versionstagg. | Backend/frontend images finns i Dockerhub med `latest`, `sha-*` eller `v*` tagg. |
| EC2 deploy | BLOCKERAR SKARP DRIFT | Appen kan fungera lokalt men inte som publik URL. | Kor `scripts/ec2-deploy.sh` pa EC2. | Frontend container, backend container och smoke test ar green pa EC2. |
| RDS databas | BLOCKERAR SKARP DRIFT | Backend startar inte eller skriver mot fel databas. | Kor `npm run check:prod -- --env-file ../.env --strict` pa EC2. | `/api/system/status` visar `database.ok = true` mot RDS. |
| Backup och restore drill | BLOCKERAR SKARP DRIFT | Bokforing och underlag kan ga forlorade eller inte ga att aterlasa. | Kor `scripts/backup-postgres.*` och aterlas till separat testdatabas. | `pg_dump` skapas, `pg_restore -l` passerar och restore drill ar dokumenterad. |
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

AliBooks kan betraktas som nara MVP-klar lokalt nar release-gaten ar gron.
AliBooks ar inte skarp produktionsklar for riktig kunddata forran punkterna ovan har externt bevis.
