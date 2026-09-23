# Kom igang snabbt med AliBooks

Den har checklistan ar for att snabbt komma igang lokalt och testa hela MVP-flodet utan att hoppa mellan olika anteckningar.

## 1. Starta databasen

Oppna Docker Desktop forst. Kor sedan i Git Bash eller terminal fran projektets rotmapp:

```bash
docker compose up db
```

Databasen ska visa att PostgreSQL lyssnar pa port `5432`.

Om backend sager `Connection to localhost:5432 refused` betyder det oftast att databasen inte ar startad.

## 2. Starta backend i IntelliJ

Oppna `backend` som Maven-projekt i IntelliJ.

Starta:

```text
CloudShopApplication
```

Backend ska starta pa:

```text
http://localhost:3000
```

Om porten ar upptagen, kontrollera om en gammal backend redan kor.

## 3. Starta frontend

Oppna en ny Git Bash fran projektets rotmapp:

```bash
npm run dev
```

Frontend ska starta pa:

```text
http://localhost:5157
```

Om port `5157` ar upptagen stoppar Vite direkt, eftersom projektet anvander `--strictPort`. Stoppa den gamla frontend-terminalen och kor `npm run dev` igen.

Du kan fortfarande kora samma kommando fran `frontend`, men projektroten har genvagar sa du slipper hamna i fel npm-projekt.

## 4. Snabb systemkontroll

Fran projektets rotmapp kan du kora:

```bash
npm run doctor
```

Den kontrollerar PostgreSQL pa `5432`, offentlig backend `/health`, skyddad backend `/system/status`, frontend pa `5157` och Docker Compose-status. Utan token verifierar den att systemstatus ger `401`; satt `ALIBOOKS_AUTH_TOKEN` eller anvand `--auth-token` for att dessutom lasa databas- och sakerhetsstatus. Om nagot inte ar igang visar den forslag pa vad du ska starta.

I AliBooks:

1. Logga in.
2. Ga till `Installningar`.
3. Kontrollera `Systemstatus`.

Bra lage:

- Backend fungerar.
- Databas fungerar.
- JWT secret ar stark.
- Stripe visas som konfigurerad om testnycklar finns.
- E-post visas som konfigurerad om SMTP finns.

## 5. Testa huvudflodet

Gor detta i ordning:

1. Skapa kund.
2. Kontrollera att felmeddelanden visas om namn, e-post eller personnummer ar fel.
3. Skapa faktura.
4. Oppna PDF.
5. Markera fakturan som skickad.
6. Registrera delbetalning eller full betalning.
7. Ga till `Bokforing` och kontrollera verifikat.
8. Ga till `Momsrapport` och kontrollera moms.
9. Exportera CSV fran fakturor, bokforing eller rapporter.

## 6. Testa Stripe utan riktig webhook

Ga till `Betalningar`.

For att simulera ett kop fran `musclefocusfitness.com`:

1. Fyll i `Manuell Stripe-forsaljning fran hemsidan`.
2. Ange datum.
3. Ange belopp inkl. moms.
4. Ange Stripe-referens, till exempel `pi_test_123`.
5. Klicka `Bokfor Stripe-forsaljning`.

AliBooks bokfor:

```text
1580 Fordran hos Stripe          debet
3041 Forsaljning tjanster 25 %   kredit
2611 Utgaende moms               kredit
```

## 7. Testa Stripe-utbetalning

Nar Stripe senare betalar ut till bank:

1. Fyll i `Stripe-utbetalning till bank`.
2. Ange datum.
3. Ange bruttobelopp fran Stripe.
4. Ange Stripe-avgift.
5. Ange payout-referens.
6. Klicka `Bokfor Stripe-utbetalning`.

AliBooks bokfor:

```text
1930 Foretagskonto               debet, netto till bank
6570 Bankkostnader               debet, Stripe-avgift
1580 Fordran hos Stripe          kredit, bruttobelopp
```

Kontrollera sedan `Saldo 1580`. Om saldot ar `0` ar Stripe avstamt.

## 8. Nar detta funkar

Da ar nasta stora steg:

1. Koppla riktig Stripe webhook.
2. Deploya backend och frontend publikt.
3. Flytta databasen till AWS RDS PostgreSQL.
4. Lagra underlag/kvitton i S3.
5. Visa allt i demo-checklistan.

## Snabb felsokning

`Connection to localhost:5432 refused`

Starta Docker Desktop och kor:

```bash
docker compose up db
```

`Port 8080/3000/5157 already in use`

En gammal process kor redan. Stoppa den, eller anvand den nya porten som terminalen visar.

`Not Found` efter ny backend-funktion

Stoppa och starta om `CloudShopApplication` i IntelliJ.

Vit frontend-sida

Oppna webblasarkonsolen och kontrollera fel. Kor ocksa:

```bash
npm run build
npm run check:views
npm run smoke:runtime
```

## Lokal kvalitetskontroll innan push

Kor detta nar du har gjort storre andringar:

```bash
npm run check:release
```

Kor detta innan push eller release:

```bash
npm run check:release:full
```

Kor detta innan deploy om Docker Desktop ar igang:

```bash
npm run check:release:full
```

Du kan ocksa kora kontrollerna separat om du vill se exakt vilket steg som failar:

```bash
npm run build
npm run check:api-contract
npm run check:ready
npm run check:backend-wiring
npm run check:docs
npm run check:docker
npm run check:prod
npm run check:env-go-live
npm run check:pilot
npm run check:startklar
npm run check:secrets
npm run check:views
npm run smoke:runtime
npm run test:backend
```

Om Maven inte finns pa datorn forsoker `npm run test:backend` anvanda Docker och en Maven Java 21-image. Det kan ta lite tid forsta gangen eftersom Docker kan behova hamta imagen.

`npm run check:startklar` visar en kort lokal MVP-status och paminner om vad som fortfarande kraver extern verifiering fore skarp drift.
