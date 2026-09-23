# Historiska reskontrasaldon

## Omfattning

Backendens GET /receivables/aging och /payables/aging samt deras /export-rutter
anvander asOf=YYYY-MM-DD som inkluderande saldodag. Utan datum anvands backendens
dagens datum. Fakturadatum ar fakturans effektiva startdatum, inte nar brevet
eller e-posten skickades. Resultatet ar en omrakning fran sparade handelser,
inte en tidigare arkiverad rapport och inte en full huvudboksavstamning.

- Fakturor daterade efter saldodagen bidrar inte till saldot.
- Betalningar pa saldodagen raknas med; senare betalningar raknas inte med.
- En faktura som ar slutbetald idag kan darfor vara oppen i en aldre rapport.
- Kundkrediter stanger originalets aterstaende fordran fran kreditfakturans datum.
  Senare aterbetalning oppnar inte fordran igen. Aterbetalningsskuld till kund
  ingar inte i denna rapport over oppna kundfordringar.
- Leverantorsmakulering stanger skulden fran makuleringsdatumet.
- Statuskolumnen beskriver betalningslaget vid saldodagen, inte historiskt
  attest-/forberedelselage. Historiska rapporter rekommenderar inga utskick
  eller betalningar. Inga externa atgarder utloses av rapporten.
- Rapportlasningen anvander en read-only REPEATABLE_READ-transaktion sa att
  faktura, betalningar och kreditunderlag inte blandas fran olika databassaldon.

## Historikkontroller

Samtliga daterade betalningar maste summera till fakturans sparade paidAmount.
Saknade datum, ogiltiga belopp, betalning fore fakturadatum/efter stangning,
statuskonflikter och krediter utan entydigt kreditunderlag ger HTTP 422 med
SETTLEMENT_HISTORY_INCOMPLETE. CSV-export skapas inte vid fel, och ingen lyckad
export registreras i revisionsloggen. Underlag maste granskas, inte fyllas i
med uppskattade betalningsdatum.

Kundbetalningar har strukturerade rader. Nya leverantorsbetalningar sparas nu i
strukturerade, daterade rader med minor-unit-skugga, referens och skapandetid.
Den gamla textkolumnen skrivs fortfarande for exportkompatibilitet men anvands
bara som fallback for aldre poster utan strukturerade rader. En strikt lasare
accepterar bara de befintliga daterade betalningsraderna och stammer av
totalsumman. Flerradiga referenser avvisas vid ny betalning. Gamla trasiga
textrader kraver kontrollerad rattelse mot underlag; denna andring migrerar
eller skriver inte om dem automatiskt.

Databasen har dessutom ett unikt identitetsskydd pa faktura, datum, belopp och
normaliserad referens. Om tva samtidiga anrop forsoker registrera samma
betalning blir resultatet en kontrollerad konflikt i stallet for en dubbel
betalningsrad.

API-svaret visar nu aven den strukturerade betalningshistoriken med datum,
belopp, `amountMinor`, valuta, referens och skapandetid. Klienten anvander den
strukturerade historiken nar den finns och faller bara tillbaka till den gamla
textkolumnen for aldre importer.

Registrerade leverantorsfakturor kan inte langre raderas, inte heller obetalda
kontantmetodsfakturor. Anvand daterad makulering. Om revisionsloggen visar tidigare
leverantorsraderingar stoppas historiska leverantorsrapporter eftersom fullstandig
historik inte kan bevisas. Raderingar/importer utan revisionsspar kan inte upptackas
av detta skydd och maste kontrolleras mot backup och tidigare bokforingssystem.

## Kvar fore skarp anvandning

- Historikens fullstandighet for importerade och tidigare raderade fakturor.
- Kontrollerad migrering fran aldre leverantorstext till strukturerade betalningsrader.
- Fullt ore-stod och avstamning av reskontra mot huvudbok/ingangsbalanser.
- Historiskt frysta kunduppgifter, forfallodatum och beloppsunderlag.
- Frontendens lokala rapporter ar fortfarande separata sammanstallningar;
  detta steg andrar backendrapporterna, inte alla lokala analyser/CSV-filer.
- inputVatOutstanding summerar hela momsen pa oppna leverantorsfakturor,
  inte kvarvarande moms efter delbetalning eller en momsdeklaration.

Anvand inte dessa tester som ett generellt go-live-godkannande.
Se [release-evidence.md](release-evidence.md) for faktiskt korda tester.
