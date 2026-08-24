# Redovisningspaket och konsultexport

Den har kontrollen beskriver vad AliBooks ska kunna lamna over till redovisningskonsult, revisor eller till dig sjalv vid granskning.

MVP-malet ar inte fullservice. Malet ar att AliBooks alltid ska kunna exportera underlag, rapporter och kontrollbevis sa att data inte blir inlast i appen.

## Vad redovisningspaketet ska innehalla

Ett professionellt redovisningspaket ska samla:

- resultatrapport
- balansrapport
- saldobalans
- huvudbok
- grundbok/verifikationslista
- momsunderlag och momskontroll
- bankavstamning
- kundreskontra
- leverantorsreskontra
- verifikationskontroll
- kontoteckenkontroll
- bokforingskedja med periodstampel och slutlig kedjekod
- SIE-fil
- SIE-exportkvittens med kontrollkod
- arsarkivkontroll
- systemdokumentation
- fragor att ta med till redovisningskonsult

## Saker delning

Redovisningspaket kan innehalla personuppgifter, fakturor, kvitton, bankrader och lonedata.

Dela darfor redovisningspaketet endast via saker kanal till:

- redovisningskonsult
- revisor
- egen saker backup
- Skatteverket eller bank nar det ar relevant

Skicka inte redovisningspaket till externa AI-verktyg. Anvand anonymiserad analys-export for AI-utkast.

## Stoppsignaler

Stoppa export eller overlamning om:

- SIE-export inte ar redo
- verifikat inte balanserar
- kritiska verifikationspunkter finns
- kritiska momspunkter finns
- bankdifferens eller kritiska bankavstamningspunkter finns
- bokforingskedjans periodstampel eller slutlig kedjekod saknas
- verifikat saknar attest
- backup inte ar verifierad
- `npm run check:handoff` failar

## Kommando

```bash
npm run check:handoff
```

Kontrollen verifierar att redovisningspaket, SIE, kvittenser, arsarkiv, systemdokumentation, frontendvy, backendtester, audit och release-gate finns kvar.
