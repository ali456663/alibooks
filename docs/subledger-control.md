# Reskontra mot huvudbok

## Omfattning

Avstamning innehaller en datumstyrd kontroll av kundfordringar (1510) och
leverantorsskulder (2440). GET `/subledger-control?asOf=YYYY-MM-DD` kraver
inloggning. Saknas datum anvands backendens aktuella datum.

Kontrollen jamfor historiskt kvarvarande fakturabelopp med daterade journalrader,
bade totalt och per faktura. Tva fel som tar ut varandra i totalsumman ger darfor
inte ett godkant resultat. Kundkrediter grupperas med ursprungsfakturan.
Journalrader utan datum eller entydig fakturakoppling kraver granskning.
Inga betalningar eller bokforingsrader skapas eller korrigeras av kontrollen.

## Status och periodlasning

- MATCHED: Kontrollerade saldon stammer per faktura. Inte ett go-live-godkannande.
- NO_DATA: Inga relevanta poster. Detta bevisar inte att importer ar fullstandiga.
- REVIEW_REQUIRED: Differens eller ofullstandig kallkoppling. Periodlasning stoppas.
- UNSUPPORTED_METHOD: Annan metod an faktureringsmetoden. Periodlasning stoppas.

Kontantmetoden kraver ett separat kontrollerat bokslutsflode innan periodlasning
kan godkannas; den far inte markeras som automatiskt avstamd med denna jamforelse.
Manuella ingangsbalanser behover verifierad reskontrakoppling. En kredit med
aterbetalningsskuld till kund kan ge ett korrekt negativt 1510-saldo men ingen
oppen kundfordran; den flaggas for granskning tills aterbetalningen hanterats.

Ofullstandig betalningshistorik och beloppsoverskridanden ger fel i stallet for
en gron kontroll. Frontend tar bort tidigare resultat vid nytt datum, ny session
eller misslyckad hamtning, och avvisar motsagelsefulla JSON-svar.

## Verifiering

- `npm run test:integration`: enhets- och PostgreSQL-integrationstester, inklusive
  delbetalningar, felkopplade fakturor, kvittande differenser, kredit/aterbetalning,
  metodkontroll, autentisering och blockerad periodlasning.
- `npm run test:reports`: validering av frontendens rapportsvar och felhantering.
- `node scripts/subledger-ui-test.mjs`: separat webblasarprov med isolerade
  mockade API-svar, Chrome och en redan startad Vite-server pa localhost:5157.
  PLAYWRIGHT_MODULE kan ange sokvagen till en befintlig Playwright-installation;
  SUBLEDGER_TEST_URL kan ange en annan lokal Vite-adress. Ingen riktig bokforingsdata
  anvands. Testbilder hamnar i operativsystemets tempkatalog/alibooks-subledger-ui.

## Kvarvarande grans

Detta ersatter inte transaktionsvis bankmatchning, fullstandighetskontroll av
importer, fullt ore-stod eller verklig backupaterlasning. Historiska kunduppgifter
ar inte frysta av kontrollen. Samtidig journalbokforing och periodlasning anvander
nu ett gemensamt databaslas; se period-write-serialization.md for omfattning och
kvarvarande grans. En godkand saldokontroll ensam ar inte tillracklig.
Se go-live-riskregister.md och historical-settlement-reports.md.
