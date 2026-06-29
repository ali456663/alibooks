# Roadmap: storsta steg kvar

Den har roadmapen ar for att komma igang snabbt och inte fastna i detaljer.

## Fokus just nu

Målet just nu ar inte att bygga allt som Fortnox/Bokio har.
Målet ar att fa AliBooks stabilt, begripligt och demo-klart.

Prioritet:

1. Lokal stabilitet
2. MVP-flode
3. Stripe-flode
4. Publik molndemo
5. Tester och presentation

## Steg 1: Lokal stabilitet

Status: viktigast forst.

Klart nar:

- Docker Desktop ar startat.
- `docker compose up db` startar PostgreSQL.
- IntelliJ startar `CloudShopApplication`.
- Frontend startar med `npm run dev`.
- `http://localhost:5173` visar appen.
- `Installningar > Systemstatus` visar att backend och databas fungerar.

Dokument:

- [kom-igang-snabbt.md](kom-igang-snabbt.md)
- [mvp-testprotokoll.md](mvp-testprotokoll.md)

## Steg 2: MVP-flode

Detta ar AliBooks grund.

Klart nar du kan visa:

- registrera konto
- logga in
- skapa kund
- skapa faktura
- generera PDF
- markera skickad
- registrera betalning eller delbetalning
- se verifikat i bokforing
- se momsrapport
- exportera CSV

Om detta fungerar har appen en riktig karna.

Anvand [mvp-testprotokoll.md](mvp-testprotokoll.md) for att bocka av huvudflodet.

## Steg 3: Stripe-flode

Vi har byggt grunden.

Det som finns:

- Stripe Checkout for AliBooks-fakturor
- Stripe webhook endpoint
- bokforing av hemsideforsaljning fran Stripe
- manuell fallback for hemsideforsaljning
- Stripe-utbetalning till bank
- historik for Stripe-utbetalningar
- avstamning av konto `1580 Fordran hos Stripe`
- CSV-export

Nasta riktiga steg:

1. Skapa Stripe test-webhook.
2. Testa `checkout.session.completed`.
3. Koppla `musclefocusfitness.com` sa Stripe skickar signal till AliBooks.
4. Kontrollera att AliBooks bokfor utan manuell fallback.

Snabb vag:

Anvand manuell Stripe-forsaljning forst.
Koppla riktig webhook senare nar lokal app och demo fungerar.

## Steg 4: Moln och publik URL

For uppgiften ar detta ett stort steg.

Klart nar du kan visa:

- publik frontend URL
- publik backend/API URL
- EC2 i AWS
- RDS PostgreSQL i AWS
- Docker/Docker Compose eller Docker images
- environment variables utan att visa hemligheter

Dokument:

- [aws-deployment-plan.md](aws-deployment-plan.md)
- [aws-rds-ec2-checklista.md](aws-rds-ec2-checklista.md)
- [go-live-checklista.md](go-live-checklista.md)
- [demo-checklista.md](demo-checklista.md)

Rekommenderad ordning:

1. Kontrollera Docker lokalt.
2. Kontrollera GitHub Actions. CI kan startas automatiskt vid push/pull request eller manuellt med `Run workflow`.
3. Bygg och pusha Docker images via Dockerhub workflow.
4. Anvand `docker-compose.prod.yml` och `.env.production.example` som produktionsmall.
5. Skapa RDS.
6. Skapa EC2.
7. Koppla backend till RDS.
8. Kor `scripts/ec2-deploy.sh` pa EC2.
9. Koppla frontend till publik backend via `/api`.
10. Kor production smoke test for frontend, backend och databas.
11. Testa demo-flodet pa publik URL.

## Steg 5: Tester

Miniminiva for demo:

- backend startar
- registrering valideras
- login fungerar
- JWT fungerar
- skapa faktura/order kraver JWT
- momsberakning fungerar
- bokforingsposter balanserar debet/kredit

Bra extra tester:

- Stripe webhook med faktura
- Stripe hemsideforsaljning
- Stripe-utbetalning
- delbetalning
- kundvalidering

## Steg 6: Presentation

Du ska visa helheten, inte all kod.

Demoordning:

1. Publik app
2. Registrera/logga in
3. Kunder
4. Fakturor
5. PDF
6. Betalning/Stripe
7. Bokforing
8. Momsrapport
9. Dockerfile och docker-compose
10. GitHub Actions
11. EC2 och RDS
12. Tester

Dokument:

- [demo-checklista.md](demo-checklista.md)

## Saker som kan vanta

Bygg inte detta forst:

- komplett lonehantering
- riktig bankkoppling via Tink/GoCardless
- Skatteverket-integration
- Swish Handel
- e-faktura
- avancerad automatisk bankmatchning
- full BAS-kontoplan
- komplett arsredovisning

De ar bra framtidssteg, men de bromsar om de kommer for tidigt.

## Min rekommenderade nasta arbetsordning

1. Starta appen lokalt enligt `kom-igang-snabbt.md`.
2. Testa MVP-flodet manuellt.
3. Skriv ner varje fel som uppstar.
4. Fixa felen i ordning.
5. Nar MVP ar stabil: deploya till moln.
6. Nar molnet fungerar: koppla riktig Stripe webhook.
7. Nar Stripe fungerar: bygg fler tester.
8. Nar testerna fungerar: forbered demo.
