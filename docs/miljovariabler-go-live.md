# AliBooks miljo variabler for go-live

Det har dokumentet forklarar de miljo variabler som maste vara kontrollerade innan AliBooks anvands med riktig kunddata, betalningar, e-post eller publik drift.

Hemligheter ska aldrig ligga i frontend, GitHub, screenshots eller dokumentation. De ska ligga i IntelliJ Run Configuration lokalt, i en ej committad `.env` pa servern, eller i en riktig secret manager.

## Lokal utveckling

For lokal test kan AliBooks starta med standardvarden, men detta ska fortfarande vara medvetet:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cloudshop`
- `SPRING_DATASOURCE_USERNAME=cloudshop`
- `SPRING_DATASOURCE_PASSWORD=cloudshop`
- `APP_FRONTEND_URL=http://localhost:5157`
- `APP_CORS_ALLOWED_ORIGINS=http://localhost:5157`
- `APP_CORS_LOCAL_DEV_ENABLED=true`
- `APP_TEST_DATA_RESET_ENABLED=false`
- `APP_BANK_RECONCILIATION_RESET_ENABLED=false`
- `JWT_SECRET` ska vara minst 32 tecken om du testar riktig inloggning.
- `JWT_EXPIRATION_MINUTES=60` ar rimligt lokalt.
- `APP_AUTH_REGISTRATION_BOOTSTRAP_KEY` kan lamnas tom lokalt; i produktion skyddar den skapandet av det forsta agarkontot.

Skapa en stark lokal JWT-hemlighet utan att spara den i kod:

```bash
npm run generate:jwt-secret
```

Kopiera bara resultatet till IntelliJ Run Configuration > Environment variables.

Skapa en separat engangsnyckel for forsta agarkontot i PowerShell:

```powershell
node -e "console.log(require('crypto').randomBytes(36).toString('base64url'))"
```

Spara den endast som `APP_AUTH_REGISTRATION_BOOTSTRAP_KEY` pa produktionsservern/secret manager. Registrerings-API:t tillater endast forsta kontot; nar agarkontot skapats stangs oppen registrering. Nyckeln maste vara minst 32 tecken, unik fran JWT-nyckeln och ska aldrig visas i frontendkoden eller Git.

## Produktion eller publik demo

For publik demo eller riktig drift ska detta vara satt:

- `APP_FRONTEND_URL` ska vara publik frontend-URL.
- `VITE_API_URL=/api` nar frontend gar via Nginx/proxy.
- `APP_CORS_ALLOWED_ORIGINS` ska matcha publik frontend-origin.
- `APP_CORS_LOCAL_DEV_ENABLED=false`.
- `SPRING_DATASOURCE_URL` ska peka pa RDS eller annan hanterad PostgreSQL.
- `SPRING_DATASOURCE_USERNAME` och `SPRING_DATASOURCE_PASSWORD` ska vara riktiga databashemligheter.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` eller `none`.
- `APP_SCHEMA_PATCH_ENABLED=false`.
- `JWT_SECRET` ska vara unik, minst 32 tecken och inte ateranvandas.
- `JWT_EXPIRATION_MINUTES` ska vara mellan 15 och 1440.
- `APP_AUTH_REGISTRATION_BOOTSTRAP_KEY` ska vara en separat, slumpad nyckel pa minst 32 tecken for forsta agarkontot.
- `APP_TEST_DATA_RESET_ENABLED=false`.
- `APP_BANK_RECONCILIATION_RESET_ENABLED=false`.

Produktions-Compose satter `APP_ENV=production`. Backend startar da inte om JWT- eller startnyckeln ar svag, ateranvand eller en mall, databasen ar lokal eller anvander standarduppgifter, CORS saknar explicita publika origin, lokal CORS ar aktiverad, automatisk schemaandring ar pa eller test-reset ar aktiverad. Kor `npm run check:prod -- --env-file din-riktiga-env-fil --strict` innan deploy; startvakten ar ett extra skydd, inte ersattning for kontrollen.

## Integrationer

Stripe:

- `STRIPE_SECRET_KEY` ska borja med `sk_test_` i test och `sk_live_` i skarp drift.
- `STRIPE_WEBHOOK_SECRET` ska borja med `whsec_`.
- Webhook ska testas innan riktiga kortbetalningar bokfors automatiskt.

SMTP:

- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_SMTP_AUTH=true`
- `SPRING_MAIL_SMTP_STARTTLS_ENABLE=true`

AI:

- `GEMINI_API_KEY`, `HF_TOKEN` eller `AI_OPENAI_API_KEY` far bara anvandas om AI-sakert lage och anonymiserad export ar kontrollerade.
- Skicka inte personnummer, adress, telefon eller e-post till extern AI.
- Anvand anonymiserad analys-JSON for demo, analys och rapportutkast.

## Stoppa direkt

Stoppa innan riktig drift om:

- `JWT_SECRET` ar `change_me_in_production` eller kortare an 32 tecken.
- `APP_AUTH_REGISTRATION_BOOTSTRAP_KEY` saknas, ar kortare an 32 tecken eller ateranvander JWT-hemligheten.
- `JWT_SECRET`, DB-adress, DB-uppgifter eller CORS-adress har kvar ett exempel-/mallvarde.
- `APP_CORS_LOCAL_DEV_ENABLED=true` i produktion.
- `APP_CORS_ALLOWED_ORIGINS` pekar pa localhost i produktion.
- `SPRING_DATASOURCE_URL` pekar pa localhost eller Docker `db` i produktion.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` anvands mot RDS utan migrationsrepetition.
- `APP_SCHEMA_PATCH_ENABLED=true` anvands mot RDS.
- Stripe eller SMTP visas som klara utan testbevis.
- AI-nycklar ligger i frontend eller GitHub.
- API-nycklar, SMTP-/databaslosenord och JWT-hemligheter som tidigare delats i chatt, loggar eller skarmbilder ska roteras fore drift.

## Kontrollkommando

```bash
npm run check:env-go-live
npm run check:prod -- --env-file .env.production.example
npm run check:prod -- --env-file din-riktiga-env-fil --strict
```

Las tillsammans med:

- [go-live-riskregister.md](go-live-riskregister.md)
- [go-live-beslut.md](go-live-beslut.md)
- [externa-go-live-bevis.md](externa-go-live-bevis.md)
- [forsta-riktiga-data.md](forsta-riktiga-data.md)
