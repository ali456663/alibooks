# AliBooks forsta riktiga data

Det har dokumentet anvands precis innan du borjar lagga in riktiga kunder, fakturor, kvitton, bankrader eller bokforingsposter i AliBooks.

Malet ar att undvika fel som blir dyra att reda ut senare: fel foretagsform, fel momsperiod, fel nummerserie, saknad backup, testdata blandad med riktig data eller betalningar som inte kan stammas av.

**Aktuellt stopp:** AliBooks pengamodell bevarar inte kronor och oren genom hela bokforingsflodet. Anvand inte systemet for riktiga bokforingsposter forran en fullstandig beloppsmigrering och avstamning ar genomford och verifierad. Fortsatt endast med avskild testdata under tiden.

`npm run check:first-real-data` kontrollerar att skydd och arbetssteg finns i projektet. Ett godkant resultat ar **inte** ett verksamhetsgodkannande och upphaver inte stoppet ovan.

## Beslut

AliBooks kan anvandas med riktig lokal MVP-data forst nar samtliga villkor nedan ar uppfyllda:

- `npm run check:release:full` ar gron.
- `npm run doctor` visar att frontend, backend och PostgreSQL svarar.
- `npm run check:use-today` ar gron.
- `npm run check:first-real-data` ar gron.
- Backendens pengamodell bekraftar fullt stod for kronor och oren; om detta saknas ska riktiga bokforingsposter stoppas.
- `npm run check:git -- --strict` ar gron efter senaste commit.
- En backup ar skapad och sparad utanfor projektmappen.
- Restore drill ar testad i separat testdatabas innan stor import eller molnflytt.
- Testdata ar rensad eller tydligt separerad fran riktiga kunder.

## Foretagsinstallningar

Kontrollera i `Installningar` innan forsta riktiga fakturan:

- Foretagsnamn och undertitel.
- Foretagsform: enskild firma nu eller aktiebolag senare.
- Bokforingsmetod: faktureringsmetoden eller kontantmetoden.
- Momsperiod och rakenskapsar.
- F-skatt.
- Kontaktmail.
- PlusGiro, OCR-standard och betalningsmottagare.
- Betalningsvillkor.
- E-postmallar for faktura och paminnelse.

Byt inte foretagsform efter att bokforing har skapats utan migrationsnotering eller ny foretagsuppsattning.

## Nummerserier

Innan skarp data:

- Fakturanummer ska vara unika och lopande.
- Verifikationsnummer ska vara sparbara och inte ateranvandas.
- Krediteringar ska skapa egen kreditfaktura/rattelse, inte skriva sonder originalet.
- Import fran Bokio ska behalla ursprungliga verifikationsnummer i beskrivning eller importlogg.

Om nummerserie verkar fel: stoppa och exportera kontrollrapport innan du fortsatter.

## Kunddata och personuppgifter

Riktig kunddata kan innehalla personnummer, adress, telefon och e-post.

Gor sa har:

- Skicka inte personnummer, adress, telefon eller e-post till extern AI.
- Anvand anonymiserad analys-export for AI, demo och test.
- Exportera kundlista och fakturor bara till saker plats.
- Kontrollera att kundnamn, e-post och personnummer ar rimliga innan faktura skapas.

## Betalningar

Innan forsta riktiga betalningen:

- Bestam om faktura betalas via bank/PlusGiro/OCR, Stripe, Swish eller kort.
- Stripe-betalningar ska stammas av mot konto `1580 Fordran hos Stripe` och bankkonto `1930`.
- Delbetalningar ska registreras med betalt belopp, datum och referens.
- Kort, Apple Pay och Swish ska granskas innan skarp produktion om kassaregister- eller kontantfakturarutin kan galla.

## Underlag och export

Innan du litar pa systemet for bokforing:

- Testa att spara kvitto/underlag.
- Testa faktura-PDF.
- Testa SIE-export, huvudbok, saldobalans, resultatrapport, balansrapport och momsrapport.
- Testa redovisningspaket till konsult.
- Spara backup tillsammans med exportbevis.

## Stoppa direkt

Anvand inte AliBooks med viktig data om:

- frontend visar vit sida eller render recovery.
- backend inte startar.
- PostgreSQL inte svarar.
- verifikat inte balanserar.
- oren inte bevaras genom faktura, moms, betalning, bokforing och export.
- momsrapporten verkar negativ eller orimlig utan forklaring.
- fakturanummer hoppar fel.
- testdata ligger kvar bland riktiga kunder.
- backup eller restore drill saknas.
- riktiga API-nycklar ligger i kod eller GitHub.

## Kommando

```bash
npm run check:first-real-data
```

Las tillsammans med:

- [anvanda-idag-beslut.md](anvanda-idag-beslut.md)
- [backup-restore-runbook.md](backup-restore-runbook.md)
- [berakningskontroll.md](berakningskontroll.md)
- [arkiv-och-andringsspar.md](arkiv-och-andringsspar.md)
- [redovisningspaket-och-konsultexport.md](redovisningspaket-och-konsultexport.md)
- [go-live-beslut.md](go-live-beslut.md)
