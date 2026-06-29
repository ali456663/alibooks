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

Oppna en ny Git Bash:

```bash
cd frontend
npm run dev
```

Frontend ska starta pa:

```text
http://localhost:5173
```

Om port `5173` ar upptagen kan Vite starta pa `5174`. Anvand den URL som terminalen visar.

## 4. Snabb systemkontroll

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

`Port 8080/3000/5173 already in use`

En gammal process kor redan. Stoppa den, eller anvand den nya porten som terminalen visar.

`Not Found` efter ny backend-funktion

Stoppa och starta om `CloudShopApplication` i IntelliJ.

Vit frontend-sida

Oppna webblasarkonsolen och kontrollera fel. Kor ocksa:

```bash
cd frontend
npm run build
```
