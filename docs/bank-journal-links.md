# Bankrad till bokforingsrad

## Omfattning

CSV-flodet galler fortfarande endast 1930 och heltals-SEK. Detta ar inte en
automatisk bankintegration eller bevis for att ett bankutdrag ar fullstandigt.

Nya bankbetalningar och bankkostnader sparar journal_entry_id i samma transaktion
som bokningen, historiken och audit. Databasen hindrar ateranvandning av samma
journalrad och borttagning av en refererad rad. Ingen koppling gissas fran text,
fakturanummer eller totalsumma.

## Aldre rader

Under Betalningar, i bankavstamningshistoriken, visas **Koppling saknas** for
aldre bokade bankrader. **Granska koppling** hamtar lediga bokforingsrader med
samma datum, konto 1930 och teckenriktigt belopp. Ingen kandidat ar forvald.
Jamfor med originalutdrag och verifikation innan **Bekrafta koppling**.
Kopplingen registreras i audit och skapar ingen ny bokforing eller betalning.
CSV-exporten innehaller Bankrad-ID och Bokforingsrad-ID for vidare granskning.

API: GET /bank-reconciliations/{id}/journal-candidates och
POST /bank-reconciliations/{id}/journal-link med {"journalEntryId": 123}.
Bada kraver JWT. Backend kontrollerar pa nytt vid sparning; samtidig anvandning
av samma journalrad, redan kopplad rad, dubblettidentitet, skipped, saknat datum,
fel konto/belopp/datum och last period stoppas. Auditfel rullar tillbaka kopplingen.
Vid 409: uppdatera historiken och granska orsaken, bokfor inte beloppet igen.

Bankavstamningen rapporterar kritiska fel for okopplade bankrader, saknade eller
felaktiga journalradskopplingar och omatchade 1930-rader, aven nar nettodifferensen
ar noll. Samma kontroll anvands vid periodstangning. Datumlosa rader kan inte
sakerhetsmassigt uteslutas med ett periodfilter. Fristaende rapporter lases inom
en repeatable-read-transaktion.

## Databasandring

DatabaseSchemaPatch och dess SQL-spegel 001_startup_schema_patch.sql innehaller
en nullable journal_entry_id-kolumn, ett unikt index och foreign key till
journal_entries. Befintliga rader behaller belopp och NULL-koppling, ingen backfill.
Utvecklingsmiljons normala schemahantering lagger till strukturen vid omstart.
Produktion med validate/avstangd startpatch kraver granskad migrering efter backup
och aterlasningsprov enligt befintlig runbook. Andra inte till ddl-auto=update
i produktion for att kringga detta. Detta steg kor ingen migrering mot din databas.

## Kvarvarande granskningsfall

- Identiska/overlappande CSV-utdrag och gamla timestamp-ID:n kan inte sakert
  dedupliceras mot varandra. Originalutdraget maste granskas.
- Endast en bankrad till en journalrad med identiskt datum/belopp stods.
  Klumpsummor, datumskillnader, delmatchning och oppningsbalanser behover ett
  separat granskat flode; de ska inte automatiskt markeras avstamda.
- Alla importerade bokforingsrader maste kontrolleras mot verkliga bankutdrag.
  En gron delkontroll ar inte ett fullstandighetsbevis eller ett go-live-beslut.
- Ore-stod, historiska ingangsbalanser, kontantmetodens bokslut och skyddad
  extern backup/aterlasning ar fortfarande viktiga MVP-hinder.

Tester: BankReconciliationServiceTest samt CloudShopApplicationIT provar
nollnettofel, korrupta kopplingar, reell PostgreSQL-unikhet/FK, samtidighet,
rollback, autentisering, bevarad historik och upprepad additiv migrering.
bank-import-ui-test.mjs provar riktig React med mockat API, explicit val,
konflikter och bekraftad koppling utan ny betalning pa dator/mobil.
