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
psql "postgresql://USER:PASSWORD@HOST:5432/cloudshop_restore" -f db/migrations/001_startup_schema_patch.sql
```

Verifiera att patchfilen fortfarande speglar backendens startup-SQL:

```bash
cd frontend
npm run check:schema
npm run check:migrations
npm run check:schema-bootstrap
```

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
