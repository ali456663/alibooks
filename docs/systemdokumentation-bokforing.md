# AliBooks systemdokumentation for bokforing

Status: arbetsunderlag for version 0.1.0. Detta dokument maste granskas mot den
exakta kodrevision som driftsatts och kompletteras med foretagets kontoplan,
rakenskapsar, valda bokforingsmetod, leverantorsrutiner och faktiska underlag.
Det ar inte ett intyg om produktionsklarhet.

Vid uppstart laggs saknade standardkonton till, men befintliga konton, deras
namn och foretagsform skrivs inte om. Tidigare uppstarter kan redan ha andrat
en lokal kontoplan; jamfor da mot arkiverad kontoplan eller verifierad backup
och dokumentera en manuell rattelse. Automatisk seedning ar inte en historik
over foretagets egna kontoplansandringar.

Kontonummer ar unika, inte NULL och inte enbart blanksteg i databasen. En
migrering stoppar vid befintliga dubbletter eller tomma nummer i stallet for
att sla ihop eller numrera om konton. Granska och dokumentera dessa poster
mot journal och arkiverad kontoplan innan migreringen kors igen. Kontrollera
fore migrering med `SELECT number, count(*) FROM accounts GROUP BY number
HAVING count(*) > 1` och `SELECT id, number FROM accounts WHERE number IS NULL
OR btrim(number) = ''` pa en aterlast kopia av databasen.

Den befintliga `/system-documentation/export` ger en periodbaserad CSV med
installningar, kontoplan, serier och kontrollpunkter. Arkivera den tillsammans
med detta dokument, kodrevision, konfiguration och behandlingshistorik for
varje driftsatt version. Texten nedan beskriver kodens regler, inte foretagets
faktiska val eller en fullstandig historik over andringar.

## Dataflode och behandlingshistorik

1. En affarshandelse registreras via ett autentiserat API. Verifikatets datum,
   belopp, konto och kallreferens valideras innan journalrader skapas.
2. `AccountingService` skapar journalrader med samma verifikationsnummer.
   `VoucherNumberService` tilldelar nummerserie. Databasen reserverar och
   forseglar verifikatet och kraver lika stor debet- och kreditsumma i oren.
3. Bokforda journalrader skyddas mot direkt andring och radering av
   databastriggers. Rattelse gor nya rader med hansvisning till originalet.
4. Underlag lagras eller lankas separat. Huvudbok, saldobalans, SIE och
   redovisningspaket ar olika las- och exportvagar fran samma journal.
5. Auditloggen registrerar viktiga API-handlingar. Den ersatter inte
   originalverifikationer eller en verifierad backup och aterlasning.

Nya databasregler avvisar UPDATE, DELETE och TRUNCATE av `audit_events`.
Startverifieringen kraver bada skyddstriggers och en isolerad aterlasning
underkanns om nagot skydd saknas eller ar avstangt. Detta ar ett skydd mot
vanliga skrivvagar, inte ett bevis mot en privilegierad databasagare som kan
stanga av triggers. Bevara separat skyddade backuper och granska databasroller.

Huvudkallor i koden: `backend/src/main/java/se/cloudshop/accounting/AccountingService.java`,
`JournalEntry.java`, `VoucherNumberService.java`, `AccountingExportController.java`,
`GeneralLedgerMinorService.java` och `db/migrations/001_startup_schema_patch.sql`.

## Automatiska konteringsregler

| Handelse | Serie | Debet | Kredit | Viktig begransning |
| --- | --- | --- | --- | --- |
| Kundfaktura med faktureringsmetoden | F | 1510 totalbelopp | Forsaljningskonto enligt momssats samt utgaende moms enligt momssats | Endast belopp som kan uttryckas exakt i hela kronor bokfors. |
| Kundbetalning med faktureringsmetoden | B | 1930 bank eller 1580 Stripe-fordran | 1510 kundfordringar | Delbetalning far inte overstiga kvarvarande belopp. |
| Stripe-forsaljning utan kundfaktura | S | 1580 Stripe-fordran | Forsaljningskonto och utgaende moms enligt vald sats | Referens maste vara unik; uppdelningen maste vara exakt i hela kronor. |
| Stripe-utbetalning | SU | 1930 bank och, vid avgift, 6570 bankkostnad | 1580 Stripe-fordran | Netto plus avgift maste motsvara bruttobelopp. |
| Kostnad med direkt betalning | K | Valt kostnadskonto och 2641 ingaende moms | Valt betalkonto | Kvitto och momsklassificering maste kontrolleras. |
| Leverantorsfaktura med faktureringsmetoden | L | Valt kostnadskonto och eventuell 2641 ingaende moms | 2440 leverantorsskuld | Sjalfakturering anvander samma kontologik men annan beskrivning. |
| Betald leverantorsfaktura med faktureringsmetoden | LB | 2440 leverantorsskuld | 1930 bank | Betalning far inte overstiga kvarvarande skuld. |
| Manuell verifikation och ingaende balans | M/IB | Konto enligt godkanda rader | Konto enligt godkanda rader | Varje rad och totalen valideras; originalunderlag maste granskas. |
| Rattelse | R | Motbokning av ursprungliga rader och eventuell ny kontering | Motsvarande kreditrader | Originalet bevaras och rattelsen lankas till originalnummer. |
| Momsavrakning och betalning | MOMS | Konton for moms och bank enligt vald atgard | Motsvarande moms-/bankkonton | Kontrollera aktuell period och avstamning fore bokning. |

Konton for 6, 12 och 25 procents moms valjs av `VatRate` och aktuell
fakturakonfiguration. Kontantmetoden bokfor kundfaktura och
leverantorsfaktura vid betalning i stallet for fakturadatum; kontrollera
kodvagarna i `AccountingService` innan metoden anvands skarpt. Automatisk
momsklassificering av en verksamhets tjanster ingar inte.

## Belopp, sparbarhet och arkiv

Legacy-belopp i journalen ar fortfarande `int` i hela SEK. `debit_minor` och
`credit_minor` ar skuggkolumner och maste matcha legacy * 100. Den nya
oresbaserade huvudboken, saldobalansen och resultatrapporten laser skuggkolumnerna exakt men
gor dem inte auktoritativa for skrivning. Produktionsstart ar blockerad tills
hela API-, skriv-, rapport- och historikmigreringen ar bevisad.

Journalens datum, verifikationsnummer, konto, beskrivning, belopp och
underlagskoppling ska kunna foljas fran original till rapport. Kontrollera
sarskilt aldre importerade eller manuella poster. `M` och `IB` ska ha
originalunderlag som kan aterlasas efter backup/restore. En CSV- eller
SIE typ 4-exporten tar med verifikationer och kontoaggregat som #IB, #UB och #RES
for valt helt rakenskapsar. Om tidigare journalrader finns tas foregaende ar
med som jamforelse; nollsaldon utelamnas. Belopp som inte kan representeras
exakt i den nuvarande kronmodellen blockerar exporten. Formatet maste fortfarande
provas i mottagande redovisningsprogram fore skarp overlamning.
Se SIE-gruppens formatbeskrivning: https://sie.se/format/.
SIE-export ar inte i sig bevis for att alla filer, kvitton och original finns.
SIE-kvittot och redovisningspaketet markerar inte exporten som klar for
overlamning om revisionskedjan visar saknade underlag, aven nar filen kan
laddas ner for granskning.
Den oresbaserade huvudboken visar ocksa registreringstid i UTC och lank till
ursprungsverifikatet for rattelser. En tom registreringstid for historiska
poster visas som saknad; systemet hittar inte pa en historisk tidpunkt.
Databasen satter registreringstid for nya direkta insattningar och nekar
explicit tom tid. Befintliga historiska rader med tom tid andras inte av
denna kontroll; de maste granskas separat.

BFN beskriver krav pa verifikationer, arkivering, systemdokumentation och
behandlingshistorik i sin [vagledning om bokforing](https://www.bfn.se/informationsmaterial/vagledningar/).
[BFN:s fragor om arkivering](https://www.bfn.se/fragor-och-svar/arkivering/)
anger sju ar efter det kalenderar da rakenskapsaret avslutades. Foretaget
maste besluta och verifiera sin egen lagrings- och gallringsrutin.

## Kontroll fore skarp anvandning

- Lasa denna dokumentation mot en fast commit och den driftsatta image-taggen.
- Kor fulla backend-/databastester, frontendens release gate och gron GitHub CI.
- Jamfor huvudbok, saldobalans, moms, reskontra och originalunderlag pa
  aterlast data. Utred varje avvikelse utan att reparera journalen tyst.
- Verifiera overenskommen backup, separat kopia, restore och atkomst till
  dokument under hela arkiveringstiden.
- Lat redovisningskunnig granska kontoplan, momssatser, kontantmetod,
  rattelser och faktisk bokforingskedja for verksamheten.

Se aven `money-safety.md`, `go-live-riskregister.md`,
`revisionsspar-integritet.md` och `backup-restore-runbook.md`.
