# MVP-testprotokoll

Anvand detta protokoll nar du testar AliBooks innan deployment eller demo.

Markera varje rad som:

```text
OK / Fel / Ej testat
```

## 1. Start och systemstatus

| Test | Forvantat resultat | Status | Anteckning |
| --- | --- | --- | --- |
| Starta Docker Desktop | Docker ar igang | Ej testat | |
| Kor `docker compose up db` | PostgreSQL lyssnar pa `5432` | Ej testat | |
| Starta backend i IntelliJ | Backend startar pa `3000` | Ej testat | |
| Starta frontend med `npm run dev` | Frontend visas pa `5173` eller den port Vite visar | Ej testat | |
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
| Exportera Stripe CSV | CSV innehaller forsäljningar och utbetalningar | Ej testat | |

## 10. Redo for moln

Du ar redo att ga till moln/deployment nar:

- huvudflodet ovan ar OK
- inga vita sidor finns
- inga backend-fel visas i IntelliJ
- `npm run build` gar igenom
- backendtester gar igenom i IntelliJ eller med `mvn test`
- Docker-databasen startar rent
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
