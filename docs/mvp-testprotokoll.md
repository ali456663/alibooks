# MVP-testprotokoll

Anvand detta protokoll nar du testar AliBooks innan deployment eller demo.

Markera varje rad som:

```text
OK / Fel / Ej testat
```

## Automatiskt bevis

Ej testat betyder att raden fortfarande ska klicktestas manuellt innan skarp drift eller demo med riktig data.
Det betyder inte att AliBooks saknar automatiska skydd. Foljande kontroller bevisar delar av MVP-flodet varje gang release-gaten kors:

- `npm run check:release` bygger frontend, kor vykontroll, releasekontroller och `smoke:runtime`.
- `smoke:runtime` kontrollerar att AliBooks inte visar vit sida, inte hamnar i render recovery och att `?reset=1` landar pa oversikt.
- `npm run test:backend` kor backendtester for auth, kunder, fakturor, betalningar, bokforing, moms, Stripe, PDF och rapportlogik.
- `check:api-contract` kontrollerar kritiska frontend/backend-kontrakt for registrering, login, kunder, fakturor, bokforing, moms och rapporter.
- `check:backup`, `check:prod` och `check:go-live-risks` kontrollerar backup/restore, produktionsmallar och go-live-risker.

## Manuellt kvar fore go-live

Detta ska fortfarande kontrolleras med riktig webblasare och testdata innan AliBooks anvands skarpt:

- kunduppgifter pa faktura: namn, personnummer, adress, postnummer, stad, telefon och e-post
- PDF visuellt: layout, betalningsinfo, F-skatt, OCR, PlusGiro och mottagare
- Stripe-flode med testnycklar och webhook innan riktiga betalningar
- underlag och kvitto/PDF: uppladdning, nedladdning och export
- restore drill till separat test database
- publik URL pa EC2/RDS och produktions-smoke mot `/api/health` och `/api/system/status`

## 1. Start och systemstatus

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Starta Docker Desktop | Docker ar igang | Ej testat | |
| Kor `docker compose up db` | PostgreSQL lyssnar pa `5432` | Ej testat | |
| Starta backend i IntelliJ | Backend startar pa `3000` | Ej testat | |
| Starta frontend med `npm run dev` | Frontend visas pa `5157` | Ej testat | |
| Oppna `Installningar > Systemstatus` | Backend och databas ar OK | Ej testat | |

## 2. Konto och inloggning

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Registrera ny anvandare | Konto skapas | Ej testat | |
| Logga in | Anvandaren kommer in i dashboard | Ej testat | |
| Logga ut | Token tas bort och login visas | Ej testat | |
| Fel losenord | Tydligt felmeddelande visas | Ej testat | |

## 3. Kunder

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Skapa kund med korrekt namn | Kunden sparas | Ej testat | |
| Felaktigt namn | Exakt valideringsfel visas | Ej testat | |
| Felaktig e-post | Exakt valideringsfel visas | Ej testat | |
| Felaktigt personnummer | Exakt valideringsfel visas | Ej testat | |
| Soka kund | Kunden hittas | Ej testat | |
| Arkivera kund | Kunden dolds fran aktiv lista | Ej testat | |

## 4. Fakturor

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Skapa faktura | Faktura skapas med fakturanummer | Ej testat | |
| Faktura visar kunduppgifter | Namn, personnummer, adress, postnummer, stad och telefon syns | Ej testat | |
| Faktura visar F-skatt | Texten finns pa fakturan | Ej testat | |
| Faktura visar betalningsinfo | PlusGiro, OCR och mottagare syns | Ej testat | |
| PDF-knapp | PDF laddas eller oppnas | Ej testat | |
| Markera skickad | Status blir skickad | Ej testat | |
| Ta bort obetald faktura | Fakturan tas bort om regler tillater | Ej testat | |

## 5. Betalningar

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Registrera full betalning | Faktura blir betald | Ej testat | |
| Registrera delbetalning | Faktura blir delbetald och kvar att betala visas | Ej testat | |
| Betalningshistorik | Betalningen syns pa fakturan | Ej testat | |
| Betalningssokning | Soker pa kund, faktura, OCR och personnummer | Ej testat | |
| Exportera betalningar CSV | CSV laddas ner | Ej testat | |

## 6. Bokforing

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Faktura skapar verifikat | `1510` debet, `3041` kredit, `2611` kredit | Ej testat | |
| Betalning skapar verifikat | `1930` debet, `1510` kredit | Ej testat | |
| Verifikat balanserar | Debet och kredit ar lika | Ej testat | |
| Verifikat har datum | Datum visas i bokforing | Ej testat | |
| Exportera bokforing CSV | CSV laddas ner | Ej testat | |

## 7. Kostnader och underlag

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Skapa kostnad | Kostnad sparas | Ej testat | |
| Moms delas upp | Netto och moms blir ratt | Ej testat | |
| Ladda upp kvitto/PDF | Underlag sparas pa kostnaden | Ej testat | |
| Exportera kostnader CSV | CSV laddas ner | Ej testat | |

## 8. Moms och rapporter

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Momsrapport | Utgaende moms, ingaende moms och moms att betala visas | Ej testat | |
| Resultatrapport | Intakter, kostnader och resultat visas | Ej testat | |
| Balansrapport | Tillgangar och eget kapital/skulder visas | Ej testat | |
| Exportera rapporter | CSV laddas ner | Ej testat | |

## 9. Stripe utan riktig webhook

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Bokfor manuell Stripe-forsaljning | `1580` debet, `3041` kredit, `2611` kredit | Ej testat | |
| Samma Stripe-referens igen | Appen stoppar dubbelbokforing | Ej testat | |
| Bokfor Stripe-utbetalning | `1930` debet, `6570` debet, `1580` kredit | Ej testat | |
| Saldo 1580 | Visar kvarvarande fordran hos Stripe | Ej testat | |
| Exportera Stripe CSV | CSV innehaller forsaljningar och utbetalningar | Ej testat | |

## 10. Redo for moln

Du ar redo att ga till moln/deployment nar:

- huvudflodet ovan ar OK
- inga vita sidor finns
- inga backend-fel visas i IntelliJ
- `npm run build` gar igenom
- backendtester gar igenom i IntelliJ eller med `mvn test`
- backendtester gar igenom med `npm run test:backend` om Maven saknas lokalt
- Docker-databasen startar rent
- JSON-backup har laddats ner och kontrollerats med `Kontrollera backupfil`
- demo-checklistan kanns trygg

Viktiga backendtester:

- `CloudShopApplicationTests`
- `UserServiceTest`
- `JwtServiceTest`
- `OrderControllerTest`
- `ProductServiceTest`
- `AccountingServiceTest`

## Fel-logg

| Datum | Var | Fel | Atgard | Status |
| --- | --- | --- | --- | --- |
| | | | | |
