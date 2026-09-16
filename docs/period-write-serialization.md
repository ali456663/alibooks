# Periodlasning och samtidiga skrivningar

## Gemensamt databaslas

AccountingService.requireUnlockedAccountingDate, AccountingPeriodLockService.closePeriod
och SettingsService:s uppdateringar anvander samma app_settings-rad (id 1) med
PESSIMISTIC_WRITE. Laset halls till den yttre transaktionens commit eller rollback.
EntityManager.refresh hamtar aven aktuella installningar nar samma transaktion
redan har en aldre version i JPA-cachen.

Periodlasningen tar laset innan den laser kontrollrapporter. En bokforing som
hinner forst maste committas eller aterstallas innan periodkontrollen fortsatter.
En periodlasning som hinner forst gor att vantande bokforing kontrollerar det nya
lasdatumet och avvisas om datumet ar last. Bokforing i senare, oppen period tillats.
Den rena forhandskontrollen checkPeriod ar fortfarande en ogonblicksbild; bara
closePeriod utfor kontroll och lasning under samma skrivlas.

Samma regel skyddar mot att en vanlig installningsuppdatering skriver over ett
nytt lasdatum. Om revisionsloggen misslyckas aterstalls periodlasningen i samma
transaktion. Ett databaslas utan yttre transaktion ar inte tillatet.

## Grans och utvecklingsregel

- Alla nya journalfloden maste kontrollera datum under detta las i samma
  transaktion som bokforingsraderna. Ett separat kontrollanrop fore POST ar inte nog.
- Direkt SQL, administrativa testdatafunktioner och andra system som skriver i
  databasen omfattas inte automatiskt. Driftens databasbehorigheter maste granskas.
- Detta ar en global skrivserialisering for nuvarande modell med en installningsrad.
  Flerforetagsstod kraver separat foretagsavgransning, inklusive las och behorighet.
- Detta gor inte alla underlags-, momsdeklarations- och importfloden atomiska.
  Deras fullstandighet och andra samtidighetsfall maste verifieras separat.
- Nya installationer maste ha initialiserade installningar innan bokforing;
  skrivkontrollen skapar inte en oskyddad standardrad om raden saknas.

Fullt ore-stod, verklig backupaterlasning och ovriga go-live-blockerare kvarstar.
Ett godkant samtidighetstest ar inte ett godkannande som enda bokforingssystem.
