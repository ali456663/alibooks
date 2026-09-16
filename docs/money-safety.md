# Beloppssakerhet och kvarvarande ore-migrering

## Status 2026-09-09

AliBooks ar fortfarande INTE godkant som enda system for skarp bokforing.
Databasens beloppsfalt, flera API-kontrakt och rapporter anvander heltal i SEK.
Detta steg andrar INTE enheten i lagrade belopp och andrar ingen historisk bokforing.

## Rattat

- Bankimportens betalning/kostnad och historik sparas atomiskt. Hela bankbeloppet
  maste matcha bokningen; overbetalning kapas inte till restsaldot. Se
  [bank-import-atomicity.md](bank-import-atomicity.md) for dubblettskyddets avgransning.
- Bank-CSV och Bokio/SIE analyserar belopp i exakta oresheltal. Bankfil med oren
  avvisas i sin helhet innan den blir bokforingsbar. SIE-forhandsvisning bevarar
  oren, men bokforingsknappen stoppar belopp som heltals-SEK-modellen inte klarar.
  Detta ar fortfarande INTE fullt ore-stod.
- Bankimport gissar inte langre belopp fran sista kolumnen eller fran saldo nar
  ett uttryckligt transaktionsbelopp ar noll. Tvetydiga kolumner, ogiltiga belopp,
  trasiga citattecken och fel kolumnantal avvisas. Negativa utbetalningar bevaras.
- En SIE-differens pa exakt 0,01 kr raknas som obalans. Gamla cachade analyser
  maste lasas om; redan avrundade rader kan inte importeras genom den gamla vyn.
  Alternativa transaktionstyper kraver manuell hantering, inte vanlig automatisk import.
  Den begransade analysen ar inte en fullstandig SIE-importmotor.


- Momsfordelning for kunddelbetalning, aterbetalning och leverantorsdelbetalning
  anvander nu 64-bitars mellanprodukter och exakt division, inte int-multiplikation
  eller float. Sista delbetalningen tar den kvarvarande avrundningsdifferensen.
- Exempel pa tidigare felrisk: 25 000 kr moms multiplicerat med 100 000 kr betalt
  overskrider int-gransen redan innan divisionen med fakturatotalen.
- Fakturapris multiplicerat med antal och netto plus moms kontrolleras for overflow.
  Ogiltiga nya fakturor avvisas fore sparning, i stallet for att fa ett negativt belopp.
- Avtalspris ganger antal valideras innan avtal och audit sparas. Fakturaexportens
  auditsumma beraknas i long och avvisas med REPORT_AMOUNT_LIMIT om den inte ryms.
- PDF skiljer total, betalt och kvarvarande belopp. Kredit och utkast markeras
  utan vanlig betalningsbegaran. Se [fakturadokument](invoice-document-snapshots.md).
- Kreditutkast anvander originalfakturans sparade belopp och rabatter, oberoende av
  tjanstens nuvarande pris. En prishojning kan inte blockera eller prissatta om krediten.
- Kostnads-API:t avvisar negativ moms och totalsummor over int-gransen.
  Kostnadsformular visar fel innan anrop for ogiltiga/fractionella belopp.
- Jackson kapar inte langre decimaltal till int/long i inkommande JSON. Ett
  decimaltal till ett heltalsfalt ger HTTP 400. Decimaltyper, dar de redan finns,
  paverkas inte av denna konfigurering.
- Fler-radiga manuella verifikationer och ingangsbalanser summeras i long fore
  balanskontrollen. Ett totalt debet pa 4 294 967 396 kan inte langre misstas for 100.
  Enskilda verifikationer over den nuvarande rapportmodellens int-grans avvisas.
- Resultat, balans, moms, saldobalans, huvudbok, kontotecken, verifikationskontroll,
  integritetsrapport och SIE-exportkvitto summerar nu med long-mellanvarden.
  Belopp som inte ryms i befintliga rapportfalt stoppas med HTTP 422 och
  felkoden REPORT_AMOUNT_LIMIT. Aven negativa gransvarden och differenser provas.
- Periodlasning och SIE-balanskontroll jamfor long-summor. Obalans kan inte doljas
  genom att debet/kredit slar runt vid int-gransen.
- Bankavstamning och kund-/leverantorsreskontra har samma long-summering och
  rapportgrans. Skydden omfattar aven bucket-summor och leverantorsexportens total.
  Se [leverantorsbetalningar](supplier-payment-safety.md) for samtidighetskontroll,
  periodregler och kvarvarande begransningar i historiska saldon.
- Frontend accepterar inte langre serverfel som resultat-, balans-, moms- eller
  saldobalansrapport. Misslyckad laddning visar fel och behaller inte gammal rapport.
  Giltiga negativa resultat bevaras; ett minus ar inte automatiskt ett berakningsfel.

## Kvar som blockerar skarp anvandning

1. **Fullt ore-stod:** databas, API, fakturor, kvitton, moms, importer, Stripe,
   aterbetalningar och exporter maste behalla samma exakta belopp hela vagen.
   Nuvarande avrundning av exempelvis en ny fakturas moms till hela kronor finns kvar.
   Avvisad decimalinput ar ett skydd, inte en losning for verkliga underlag med oren.
2. **Bredare beloppsmodell:** skydden ovan stoppar for stora rapportsummor, men
   utokar inte rapporternas kapacitet eller ger ore-stod. Frontendens egna analyser
   och ovriga beloppsfloden maste granskas separat. Historisk reskontra och
   transaktionsvis bankmatchning ar inte fardigverifierade.
   Rapportsystemet ska migreras samlat innan gransen kan tas bort.
3. **Historisk avstamning:** redan bokford moms kan vara fel fran den tidigare
   berakningen. Jamfor mot underlagen; inga historiska poster skrivs over av denna fix.
   Eventuella rattelser maste goras genom granskat rattelseflode.
4. **Momsregler:** hardkodad 25-procentsregel ar inte en generell momsmotor.
   Ratt momssats och behandling for respektive tjanst maste verifieras separat.
5. **Verklig backup och verksamhetsprov:** [backup-runbook](backup-restore-runbook.md)
   samt [Stripe-kontrakt](stripe-booking-safety.md) har separata kvarvarande krav.

## Krav pa nasta migrering

- Valj en enhet och precisionsmodell uttryckligen; byt aldrig innebord av befintliga
  heltalsfalt utan versionssatt migrering och verifierad aterlasningsbar backup.
- Prova gamla fakturor, krediter, delbetalningar, importer och oppna saldon i en kopia.
- Anvand exakta decimaler eller heltal i minsta valutaenhet, aven i summeringar.
- Testa 0,01 kr, belopp med 6/12/25 procent, rabatt, delbetalning och sista
  avrundningsdifferens genom databas, bokforing, PDF/export och betalprovider.
- Jamfor rapporter och ingangsbalanser fore/efter. Godkann inte bara en gron build.
