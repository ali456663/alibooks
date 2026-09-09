# Databastester for AliBooks

## Korning

Kor `npm run test:integration` fran projektroten med Docker igang.
Kommandot kor `mvn -B clean -Pintegration verify` i en separat Compose-miljo.
PostgreSQL 16 anvander minnesbaserad lagring, inga publicerade portar och en ny
databas som heter `alibooks_test`. Inga produktionsnycklar eller `.env` lases in.
Containrarna tas bort aven om testerna misslyckas. Rapporter finns i
`backend/target/surefire-reports` och `backend/target/failsafe-reports`.

GitHub Actions kor samma Maven-profil med en separat `alibooks_test`-databas.
Testklassen vagrar starta utan `TEST_DATABASE_URL` som pekar pa detta databasnamn.
Anvand aldrig detta namn for riktig bokforing: testprofilen skapar och tar bort tabeller.

## 20 beteenden som provas

1. Hela Spring Boot-applikationen startar med PostgreSQL, seeders och HTTP-server.
2. Faktura-API nekar anrop utan autentisering.
3. Utkast far fakturanummer men bokfors inte.
4. Skickad faktura sparar balanserat verifikat och status.
5. Upprepad markering som skickad dubbelbokfor inte vid sekventiella anrop.
6. Databasfel pa kreditraden aterstaller alla rader och fakturastatus.
7. Manuell bokning aterstalls vid fel pa andra raden.
8. Kostnad och verifikat aterstalls tillsammans vid bokforingsfel.
9. Fel i revisionsloggen aterstaller ett nytt fakturautkast.
10. Fel i revisionsloggen aterstaller betalningshistorik och betalningsverifikat.
11. Delbetalning och slutbetalning sparas med ratt aterstaende belopp.
12. Upprepad identisk betalning nekas utan fler bokforingsrader.
13. Misslyckad kreditering aterstaller kreditfaktura och originalets status.
14. Last datum stoppar bokning utan kvarlamnade rader.
15. Overbetalning nekas utan att bokforing eller betalstatus andras.
16. Misslyckad revisionslogg lamnar fakturautkastet kvar vid raderingsforsok.
17. Bokford faktura kan inte raderas.
18. Kreditfaktura reverserar originalets kontosaldon.
19. Misslyckad aterbetalning aterstaller belopp och verifikat.
20. Samtidiga manuella verifikat vantar pa commit och far olika nummer.

Testerna har ingen omgivande testtransaktion som automatiskt doljer sparfel.
Kontrollerna laser databasen efter att produktionskodens transaktion avslutats.
Skrivfel framkallas med en PostgreSQL-trigger efter forsta debetraden. Fel i
revisionsloggen framkallas separat for att testa hela affarsoperationens rollback.

## Betalningsskydd: ytterligare 34 testfall

- Fyra ogiltiga betalningsbelopp: noll, negativt, minsta och storsta Java-heltal.
- Samma fyra ogiltiga belopp for aterbetalningar.
- Utelamnat belopp respektive utelamnad body anvander endast kvarvarande saldo,
  bade for betalning och aterbetalning (fyra fall).
- Betalningsdatum och aterbetalningsdatum fore fakturadatum nekas (tva fall).
- Fem samtidighetstest: tva delbetalningar, identisk betalning, overbetalning,
  for stor sammanlagd aterbetalning och dubbel kreditering.
- Betalning mot saknad faktura ger 404 utan bokforingsrader.
- Sju JSON-varden som inte far omvandlas till betalningsbelopp provas via HTTP:
  0.5, 50.9, text, booleskt varde, objekt, lista och heltal utanfor Integer.
  Samma sju fall provas for aterbetalningar.

Samtidighetstesterna haller den forsta transaktionen oppen tills det andra
anropet vantar. Darefter kontrolleras sparat saldo, historik och verifikat.
De provar manuella API-operationer, inte bankens faktiska overforing av pengar.

## Andringarna

Bokforingstjansten och fakturornas lokala skrivoperationer har gemensamma
transaktioner. Kostnadsskapande inkluderar bade kostnad, verifikat och revisionslogg.
Verifikationsnummer reserveras med ett PostgreSQL advisory transaction lock per
serie, som slapps vid commit eller rollback. Ingen ny produktionstabell behovs.

Manuell betalning, aterbetalning, kreditering, skickad-markering och radering
laser fakturaraden med PostgreSQL FOR UPDATE innan saldo och status lases.
Laset kraver en aktiv transaktion och slapps vid commit eller rollback.
Noll och negativa belopp nekas i stallet for att tolkas som full betalning.
Betalnings- och aterbetalnings-API:erna avvisar decimaler och JSON-typkonvertering.
Frontend behaller uttryckliga noll/tomma varden och stoppar ogiltiga belopp.

Nuvarande beloppsmodell ar hela SEK. Stod for oren maste byggas konsekvent i
databas, API, berakningar, bankimport och Stripe innan decimalbelopp kan anvandas.

## Kvar fore skarp anvandning

Detta ar tekniska regressionstester, inte ett intyg om att hela systemet ar klart.
Granska fortfarande samtidiga betalningar mot samma faktura fran olika kanaler
(e-postfloden och andra skrivare anvander inte fakturaradlaset),
Stripe-belopp och avrundning, e-postleverans tillsammans med databasfel samt
aterlasning av bade databas och uppladdade underlag. Dessa floden bevisas inte av
testet for samtidiga verifikationsnummer. Kontroller som bara letar efter text i
koden bevisar inte att berakningar eller affarsfloden fungerar vid korning.

Molndrift, backup/restore och externa betalnings- och e-postintegrationer behover
separata testbevis enligt befintligt go-live-riskregister.

Stripe-hardningen kontrollerar nu `amount_total`, `payment_status`, valuta och
fakturastatus. Belopp med oren avvisas i stallet for att trunkeras. Syntetiskt
signerade webhook-tester provar verklig databas, dubbletter, fordrojda betalningar,
konflikt med manuell betalning och rollback/retry. Detta ersatter inte ett riktigt
Stripe-testmode-prov fran hemsidan genom webhook till avstamning av utbetalning.

Se [Stripe-kontrakt och kvarvarande blockerare](stripe-booking-safety.md).
