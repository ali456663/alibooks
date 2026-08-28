# AliBooks CI-handoff efter push

Det har dokumentet anvands nar lokal release ar gron och koden ska bevisas i GitHub.

Aktuell remote for projektet:

```bash
origin https://github.com/ali456663/alibooks.git
```

## Steg efter lokal commit

1. Kor `npm run check:release`.
2. Kor `npm run check:git -- --strict`.
3. Pusha med `git push`.
4. Oppna GitHub Actions i `https://github.com/ali456663/alibooks/actions`.
5. Kontrollera workflow `CI`.
6. Kontrollera att jobben ar grona:
   - `Backend build and test`
   - `Frontend release gate`
   - `Docker build`
7. Oppna jobbsummaries i GitHub Actions och kontrollera att de visar AliBooks-bevis.
8. Ladda ner artifacts vid behov:
   - `backend-surefire-reports` for Maven/Surefire-testloggar
   - `frontend-dist` for byggd frontendbundle
9. Kor `npm run check:sync` lokalt efter push.
10. Kor `npm run check:post-push -- --require-pushed`.
11. Kor `npm run check:release-traceability`.

## Dockerhub efter CI

Dockerhub workflow kan koras manuellt eller via en versionstagg.

Krav:

- `DOCKERHUB_USERNAME` finns som GitHub secret.
- `DOCKERHUB_TOKEN` finns som GitHub secret.
- Backend image publiceras som `cloudshop-backend`.
- Frontend image publiceras som `cloudshop-frontend`.
- Taggar ska vara sparbara: `latest`, `sha-*` eller `v*`.

## Om CI failar

Vanliga fel och vad du gor:

- `OrderController` constructor eller `CreateOrderRequest`: backendtesterna ar inte uppdaterade efter controller/record-andring.
- `npm audit`: frontend dependency-audit hittade en aktuell risk och maste granskas innan deploy.
- `npm run check:release`: release-gaten stoppade en lokal MVP-regel, kontrollera loggen for forsta FAIL.
- `Docker build`: kontrollera Dockerfile, `npm ci`, Java 21 och att inga lokala filer saknas i GitHub.
- Artifacts saknas: kontrollera att workflow-steget med `actions/upload-artifact@v4` har korts och att jobben skriver `GITHUB_STEP_SUMMARY`.
- Node-varning: CI ska anvanda Node 24, inte Node 20.
- Databasfel: CI ska anvanda PostgreSQL 16 service och explicit `SPRING_JPA_HIBERNATE_DDL_AUTO=update`.

## Go-live-grans

GitHub Actions bevisar bara att koden bygger i GitHub.
Skarp drift kraver fortfarande Dockerhub-image, EC2/RDS-smoke, backup/restore drill, Stripe, SMTP och `npm run check:go-live-decision`.

Se aven [post-push-verifiering.md](post-push-verifiering.md) for den korta checklistan efter `git push`.
