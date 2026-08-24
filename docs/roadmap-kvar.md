# Roadmap: storsta steg kvar

Den har roadmapen ar for att komma igang snabbt och inte fastna i detaljer.

## Fokus just nu

Malet just nu ar inte att bygga allt som Fortnox/Bokio har.
Malet ar att fa AliBooks stabilt, begripligt och demo-klart.

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
- `http://localhost:5157` visar appen.
- `Installningar > Systemstatus` visar att backend och databas fungerar.

Dokument:

- [kom-igang-snabbt.md](kom-igang-snabbt.md)
- [mvp-testprotokoll.md](mvp-testprotokoll.md)
- [professionell-bokforing-loop.md](professionell-bokforing-loop.md)
- [ci-handoff-efter-push.md](ci-handoff-efter-push.md)

Efter stor frontend-andring:

```bash
cd frontend
npm run check:release
npm run check:release:full
npm run check:acceptance
npm run build
npm run check:ready
npm run check:ci-handoff
npm run check:backend-wiring
npm run check:docker
npm run check:prod
npm run check:go-live-decision
npm run check:startklar
npm run check:mvp-use
npm run check:operations
npm run check:use-today
npm run check:calculations
npm run check:retention
npm run check:audit-integrity
npm run check:period-close
npm run check:handoff
npm run check:dependencies
npm run check:audit
npm run check:release-traceability
npm run check:secrets
npm run check:evidence
npm run check:git
npm run check:sync
npm run check:views
npm run smoke:runtime
npm run test:backend
```

`npm run check:release` kor releasegrinden: frontend build, professionell loop, API-kontrakt, readiness, backend-wiring, dokumentation, secrets, Docker-konfig, vykontroll och runtime-smoke i en fast ordning.
`npm run check:release:full` kor samma releasegrind plus backendtester och lokala Docker-image-builds. Anvand den innan push, deploy och skarp demo.
`npm run check:acceptance` kontrollerar att MVP-testprotokollet skiljer automatiska bevis fran manuella go-live-tester.
`npm run check:ready` ar en snabb statisk kontroll for att se att projektet fortfarande har de viktigaste byggstenarna for skarp MVP: CI, Docker, env-mallar, professionell 20-stegsplan, regelkontroll, Startklar, Stripe/SMTP/JWT-punkter och dokumentation.
`npm run check:ci-handoff` kontrollerar [ci-handoff-efter-push.md](ci-handoff-efter-push.md): vad som ska granskas efter `git push`, GitHub Actions-jobb, Dockerhub workflow, secrets, jobbnamn, synk och vanliga CI-fel.
`npm run check:backend-wiring` fangar vanliga Java-fel dar controller-constructors eller request-records har andrats men tester/kod inte har uppdaterats.
`npm run check:docker` kontrollerar Dockerfiler, compose, port 5157, backend-port 3000 och nginx `/api`-proxy.
`npm run check:prod` kontrollerar produktionsmallen for EC2/RDS: `.env`, CORS, `/api`, JWT, test-reset, Docker Compose och smoke scripts. Pa EC2 kan den koras strikt med `npm run check:prod -- --env-file ../.env --strict`.
`npm run check:go-live-decision` kontrollerar sista beslutet mellan lokal MVP och skarp drift: GitHub Actions, Dockerhub, EC2/RDS, backup/restore, Stripe, SMTP, release-sparbarhet och redovisningskonsult-export.
`npm run check:startklar` ger en kort lokal MVP-bedomning: vad som ar redo, vad som fortfarande kraver extern verifiering och varfor skarp drift maste vanta tills GitHub/Dockerhub/EC2/RDS/backup/Stripe/SMTP ar bevisade.
`npm run check:mvp-use` kontrollerar 20 praktiska steg for anvandningsklar lokal MVP: lokal start, inloggning, kund/faktura, betalning, bokforing, bank-CSV, underlag, moms, rapporter, lon-MVP, bokslut, backup, restore, sakerhet, CI, git och go-live-beslut.
`npm run check:operations` kontrollerar Driftcenter och drift-runbook: incidentlogg, releasejournal, rollback-plan, backup/smoke-test, local doctor, runtime smoke och produktions-smoke.
`npm run check:use-today` ger sista lokala anvandningsbeslutet: vad som maste vara gront for att jobba i AliBooks idag, vilka stoppsignaler som betyder fixa forst, och varfor skarp produktion fortfarande kraver externa bevis.
`npm run check:calculations` kontrollerar berakningsintegritet: netto+moms=total, negativa kreditfloden, delbetalningsavrundning, moms, verifikationsbalans, Stripe 1580, leverantorsfakturor och lonens arbetsunderlag.
`npm run check:retention` kontrollerar arkiv och andringsspar: fakturor, verifikat, kvitton, kunder, leverantorsfakturor, bankimport, periodlasning och hard delete-regler.
`npm run check:audit-integrity` kontrollerar revisionsspar-integritet: audit-handelser, SHA-256-kedja, CSV-export, backupkoppling och backendtester som visar att andrad historik ger ny auditstampel.
`npm run check:period-close` kontrollerar periodstangning: blockerare, varningar, attest, bankavstamning, momsbevis, sena verifikat, periodstampel och slutlig kedjekod innan periodlasning eller bokslut.
`npm run check:handoff` kontrollerar redovisningspaket: SIE, SIE-kvittens, resultat, balans, huvudbok, saldobalans, moms, bank, reskontra, arsarkiv, systemdokumentation och saker delning till konsult.
`npm run check:dependencies` kontrollerar att frontend har lockfile, att direkta runtime-beroenden och buildverktyg ar lasta i `package-lock.json`, att Docker anvander `npm ci`, och att online-audit-kommandot ar dokumenterat fore skarp deploy.
`npm run check:audit` kor live audit mot npm-registret och ska vara gron fore skarp deploy.
`npm run check:release-traceability` kontrollerar att package-version, git branch/commit, GitHub-sync, Dockerhub `sha-*`/`v*`-taggar och EC2 `IMAGE_TAG` hanger ihop.
`npm run check:secrets` stoppar riktiga Stripe-, AI-, AWS- eller private-key-liknande hemligheter fran att hamna i GitHub.
`npm run check:evidence` kontrollerar att releasebeviset fortfarande matchar readiness, testbevis och riskregister.
`npm run check:git` visar om viktiga filer ligger lokalt utan att vara commitade. Efter release-commit ska `npm run check:git -- --strict` vara gron.
`npm run check:sync` visar om lokala commits ar pushade till GitHub. Den ska vara gron efter push och innan du litar pa GitHub Actions.
`npm run check:views` kontrollerar att varje menyknapp har en faktisk renderad vy, sa appen inte blir vit av en trasig navigation.
`npm run test:backend` kor backendtester med lokal Maven om den finns, annars via Docker med Java 21.

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
- regelvarning i `Regelkontroll` och `Startklar` for kort/Swish/Stripe innan skarp anvandning

Nasta riktiga steg:

1. Skapa Stripe test-webhook.
2. Testa `checkout.session.completed`.
3. Koppla `musclefocusfitness.com` sa Stripe skickar signal till AliBooks.
4. Kontrollera att AliBooks bokfor utan manuell fallback.
5. Kontrollera om hemsidebetalningar via kort/Apple Pay/Swish ska hanteras med certifierat kassaregister eller tydlig kontantfaktura-rutin.

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
- frontend runtime smoke test som oppnar AliBooks i Chrome

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
