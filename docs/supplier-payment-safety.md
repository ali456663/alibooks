# Leverantorsbetalningar och reskontra

## Skydd i backend

- Statusandring/betalning, makulering och borttagning laser samma fakturarad
  med SELECT FOR UPDATE innan saldo och status lases. Laset lever tills
  transaktionen ar klar, sa tva samtidiga anrop inte anvander samma gamla saldo.
- Delbetalning, journalposter, betalhistorik och revisionslogg omfattas av
  samma transaktion. Fel ska aterstalla hela anropet, inte bara fakturastatus.
- Makulerade fakturor kan inte ateraktiveras via betalning/statusandring.
  Fakturor med betalningar kan inte makuleras utan en separat betalningsrattelse
  eller flyttas tillbaka till status booked.
- Betalningsmodellen avvisar noll, negativa belopp och overbetalning. Belopp
  klipps inte till totalsumman och int-overflow tillats inte.
- En redan bokford faktura fran en last period kan betalas pa ett oppet datum.
  Ingen ny ursprungsbokning skapas da. Vid kontantmetod kontrolleras det datum
  dar betalningen faktiskt bokfors. En ny bokning i en last period ar fortsatt blockerad.
- Bankavstamning, kundreskontra och leverantorsreskontra summerar i long och
  kontrollerar rapportfaltens grans. For stora belopp ger REPORT_AMOUNT_LIMIT,
  inte ett negativt eller falskt balanserat resultat.

## Inte samma sak som produktionsklarhet

Detta skickar inte pengar till en bank. Registrerad betalning ar fortfarande
en bokforingsatgard som maste stammas av mot verklig banktransaktion.

Foljande aterstar innan rapporterna kan anvandas som underlag for skarpt avslut:

1. Full ore-migrering enligt [money-safety.md](money-safety.md).
2. Historisk reskontra raknas nu fran daterade betalningar, krediter och
   makuleringar. Ofullstandig historik stoppas, men importer och tidigare
   raderingar maste fortfarande stammas av mot riktiga underlag. Frontendens
   lokala sammanstallningar omfattas inte av backendandringen.
   Se [historiska reskontrasaldon](historical-settlement-reports.md).
3. Bankrapporten jamfor summerade rorelser pa konto 1930 med bankrader markerade
   booked. Noll differens bevisar inte att varje transaktion matchats korrekt.
   Stabil bankradsidentitet, retry/dubblettkontroll och sparade lankar till
   underlag/verifikat aterstar att verifiera i hela importflodet.
4. Frontendens lokala sammanstallningar och moms pa delbetalda leverantorsfakturor
   maste granskas tillsammans med vald bokforingsmetod. Backendens granskontroll
   ersatter inte denna kontroll.
5. Verklig aterlasningsbar backup, avstamda ingangsbalanser och extern kontroll
   av bokforingen kravs fore beslut om skarp anvandning.

Testbevis och kvarvarande driftkrav finns i [release-evidence.md](release-evidence.md)
och [go-live-riskregister.md](go-live-riskregister.md).
