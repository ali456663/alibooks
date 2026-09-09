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

## Andringarna

Bokforingstjansten och fakturornas lokala skrivoperationer har gemensamma
transaktioner. Kostnadsskapande inkluderar bade kostnad, verifikat och revisionslogg.
Verifikationsnummer reserveras med ett PostgreSQL advisory transaction lock per
serie, som slapps vid commit eller rollback. Ingen ny produktionstabell behovs.

## Kvar fore skarp anvandning

Detta ar tekniska regressionstester, inte ett intyg om att hela systemet ar klart.
Granska fortfarande samtidiga betalningar mot samma faktura fran olika kanaler,
Stripe-belopp och avrundning, e-postleverans tillsammans med databasfel samt
aterlasning av bade databas och uppladdade underlag. Dessa floden bevisas inte av
testet for samtidiga verifikationsnummer. Kontroller som bara letar efter text i
koden bevisar inte att berakningar eller affarsfloden fungerar vid korning.

Molndrift, backup/restore och externa betalnings- och e-postintegrationer behover
separata testbevis enligt befintligt go-live-riskregister.
