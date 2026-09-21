# Bankimport: atomisk bokning och aterforsok

## Avgransning

CSV-importen ar fortfarande ett granskningsflode for ett bankkonto pa 1930, med
heltals-SEK. Den ar inte en bankkoppling eller ett bevis pa fullstandig avstamning.
Saknat bankdatum ersatts aldrig med dagens datum; raden bevaras som odaterad och
blir en kritisk granskningspunkt i avstamningen.
Fullt ore-stod och flera bankkonton aterstar. Nya bankbokningar sparar nu en
unik journalradskoppling; aldre bankrader lamnas okopplade for uttrycklig granskning.
Inga tidigare belopp, bokforingsposter eller underlagsfiler andras av detta steg.

## Sparning

- `POST /bank-import/invoices/{id}/paid` tar betalning och `bankRow` i ett anrop.
- `POST /bank-import/expenses` tar kostnad och `bankRow` i ett anrop.
- Bada kraver JWT och uttrycklig bankrad. De gamla manuella API-anropen utan bankrad
  fungerar som tidigare. Ny frontend faller inte tillbaka till dem vid 404.
- Datum och HELA beloppet maste stamma med bokningen. Overbetalningar minskas inte
  automatiskt till restsaldo. Kostnadens netto plus moms maste motsvara utbetalningen
  och betalkontot maste vara 1930. Oren avvisas tills beloppsmodellen migrerats.
- Manuella betalningar utan bankrad maste ha en betalreferens. Bankimportens bankrad-ID
  ar den idempotenta identiteten och behovet inte ersatt av en fri textreferens.
- Ett PostgreSQL advisory transaction lock per bankRowId serialiserar samtidiga
  forsok. Redan bokad eller overhoppad rad ger 409; historik maste granskas.
- Betalning/kostnad, verifikationer, bankhistorik och audit sparas i samma transaktion.
  Fel i journal eller audit rullar tillbaka allt. Periodlasning respekteras.
- Separat `POST /bank-reconciliations` tillater endast status skipped, inte fristaende
  pastadda bokningar. Skipped kan tas bort bara i oppen period och under samma radlas.
  Lokal massrensning ar fortsatt separat testfunktion, avstangd som standard.

## Frontend

Bankdatum maste vara verkligt YYYY-MM-DD; ogiltiga datum ersatts inte med dagens
datum. CSV-filens rader far SHA-256-baserade identiteter av konto 1930, datum,
belopp, referens, beskrivning och forekomstnummer for identiska rader. Aterimport
av oforandrade rader ger samma identitet, oberoende av andra raders ordning.

**Detta ar inte bankens transaktions-ID.** Andrad banktext eller referens kan ge ny
identitet. Identiska transaktioner i overlappande delutdrag kan vara tvetydiga.
Gamla timestamp-baserade bankrader maste avstammas manuellt mot tidigare bokningar
innan ny import. Skapa inte ett nytt ID for att kringga en konflikt. Granska dessa
fall mot originalutdraget; flera bankkonton far inte blandas i denna modell.

UI tar bort en bankrad forst efter ett lyckat serversvar. Fel, ogiltigt svar,
natverksavbrott och 409 skapar ingen lokal framgangshistorik. Ett borttappat svar
kan betyda att servern redan sparat; kontrollera historiken fore aterforsok.
Tom historik fabriceras inte nar hamtning misslyckas.

Matchningsforslag kraver unik referenstraff eller unikt kundnamn plus belopp.
Enbart lika belopp valjer inte en faktura. Forslag maste fortfarande granskas.
Att visa en faktura registrerar inte en bankmatchning.

## Verifiering

Se [bank-journal-links.md](bank-journal-links.md) for transaktionskontrollen,
manuell koppling av aldre rader, databasandring och kvarvarande begransningar.

`npm run test:integration` inkluderar verklig PostgreSQL, samtidiga bankforsok,
rollback, autentisering, datum/belopp, overbetalning och periodlasning.
`npm run test:reports` inkluderar parser, identiteter, matchningsforslag och HTTP-fel.
`scripts/bank-import-ui-test.mjs` provar den riktiga React-vyn med helt mockade
API-svar, separat webblasarkontext och inga anrop till verklig databas.
Mobiltestet kontrollerar att bankknappar och sidhuvud ryms inom 390 pixlar.

Se release-evidence.md for faktiskt utforda korningar. Lokal testframgang ar inte
GitHub CI-bevis eller godkannande for skarp bokforing.
