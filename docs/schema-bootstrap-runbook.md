# AliBooks schema bootstrap runbook

Den har runbooken ar for forsta kontrollerade schema-starten pa RDS eller annan produktionslik databas.
Malet ar att backend inte ska skapa eller andra produktionstabeller dolt vid startup.

## Princip

Produktion ska kora med:

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
APP_SCHEMA_PATCH_ENABLED=false
```

Det betyder:

- Hibernate kontrollerar schema men muterar inte RDS automatiskt.
- `DatabaseSchemaPatch` kor inte `ALTER TABLE` vid produktionsstart.
- Schemaandringar ska vara testade, sparade och repeterbara.

## 1. Skapa schema fran testad release-databas

Anvand en lokal eller staging-databas dar backendtester och release gate ar grona.
Exportera endast schema, inte personuppgifter eller bokforingsdata:

```bash
pg_dump --schema-only --no-owner --no-privileges \
  --host localhost \
  --port 5432 \
  --username cloudshop \
  --dbname cloudshop \
  --file backups/alibooks-schema.sql
```

Kontrollera filen:

```bash
psql "postgresql://USER:PASSWORD@HOST:5432/cloudshop_restore" -f backups/alibooks-schema.sql
```

## 2. Kor patch-SQL mot restore/staging

Efter basschemat kor du den kontrollerade patchfilen:

```bash
psql -v ON_ERROR_STOP=1 --single-transaction "postgresql://USER:PASSWORD@HOST:5432/cloudshop_restore" -f db/migrations/001_startup_schema_patch.sql
```

Verifiera att patchfilen fortfarande speglar backendens startup-SQL:

```bash
cd frontend
npm run check:schema
npm run check:migrations
npm run check:schema-bootstrap
```

CI:s backendintegrationstest kor dessutom hela SQL-filen mot isolerad PostgreSQL 16
med ett bokfort testverifikat. Det kontrollerar att beloppen ar oforandrade,
att journalens integritetssparr fungerar efter aterkorning och att filen avbryts vid en
redan skadad verifikatrad. Detta provar aterkorning pa ett redan patchat schema,
inte alla mojliga aldre produktionsscheman. Kor darfor alltid filen pa en
aterstalld kopia av den aktuella produktionsdatabasen fore skarp migrering.

## 3. Starta backend mot testdatabasen med validate

Starta backend med samma schema-policy som produktion:

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
APP_SCHEMA_PATCH_ENABLED=false
```

Kontrollera:

```text
/api/health
/api/system/status
```

Forvantat:

```text
database.ok = true
```

## 4. Forst darefter RDS

Pa RDS gor du samma ordning:

1. RDS snapshot eller tom ny RDS innan import.
2. Importera testad `backups/alibooks-schema.sql`.
3. Kor `db/migrations/001_startup_schema_patch.sql`.
4. Kor `npm run check:prod -- --env-file ../.env --strict` pa EC2.
5. Starta backend med `validate` och `APP_SCHEMA_PATCH_ENABLED=false`.
6. Kontrollera `/api/system/status`.

## Separat databasroll for appen

Importera schema och kor migrationer som en separat migrationsagare. Anvand aldrig
webbappens inloggning for schemaimport eller `db/migrations/001_startup_schema_patch.sql`.
Tabeller, sekvenser och skyddande triggerfunktioner maste tillhora migrationsagaren, inte webbappen.
Detta galler aven om `ddl-auto=validate` och schema-patchen ar avstangda.

Exempel for en dedikerad RDS-databas dar migrationsagaren redan har importerat
hela schemat. Kor som databasadministrator, byt databas- och rollnamn vid behov:

```sql
CREATE ROLE alibooks_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
-- Satt ett unikt losenord via psql:s interaktiva \password alibooks_app.
REVOKE CREATE ON DATABASE alibooks FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE alibooks TO alibooks_app;
GRANT USAGE ON SCHEMA public TO alibooks_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO alibooks_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO alibooks_app;
```

Kor dessutom `ALTER DEFAULT PRIVILEGES FOR ROLE <migrationsagare> IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO alibooks_app` och motsvarande
`GRANT USAGE, SELECT ON SEQUENCES TO alibooks_app` for framtida migrationer. Ge inte
`alibooks_app` medlemskap i migrations-, schema- eller databasagarens roll,
och ge inte `TRIGGER`, `TRUNCATE`, `REFERENCES`, schema-`CREATE` eller databas-`CREATE`.
Verifiera grants efter varje migration och starta appen med
`SPRING_DATASOURCE_USERNAME=alibooks_app` och dess separata losenord.

Produktionsstarten avbryts om approllen kan aga eller andra skyddade tabeller,
saknar kritiska tabeller, har privilegier som kan kringga triggers, eller om
behorighetskontrollen inte kan koras. Prova forst pa en isolerad restore.
Denna kontroll ersatter inte migreringskontroll, backup eller granskning av
kontobehorigheter pa RDS-niva.

## Stoppa om

Stoppa go-live om nagot av detta hander:

- `column does not exist`
- `relation does not exist`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` anvands i produktion
- `APP_SCHEMA_PATCH_ENABLED=true` anvands i produktion
- restore/staging inte har testats
- backup eller RDS snapshot saknas

## Viktigt

Det har ar en MVP-runbook, inte ett fullstandigt migrationsramverk som Flyway eller Liquibase.
Nar AliBooks borjar anvandas skarpt bor nasta tekniska steg vara att ersatta startup-patchar med versionerade migrationer.
