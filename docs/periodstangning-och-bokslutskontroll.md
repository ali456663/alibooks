# Periodstangning och bokslutskontroll

Den har kontrollen beskriver vad AliBooks ska kontrollera innan en period lases eller anvands som underlag for moms, rapport, bokslut eller molnflytt.

AliBooks ar inte en ersattning for redovisningskonsult, men systemet ska hjalpa dig att inte stanga en period med uppenbara fel.

## Nar kontrollen ska koras

Kor periodstangningskontrollen innan:

- periodlasning
- momsrapport eller momsbetalning
- resultat- och balansrapport som ska delas externt
- arsbokslut, NE/INK2/K2-arbetsunderlag eller export till konsult
- backup, restore drill eller molnflytt med riktig data

## Saker som stoppar periodlasning

AliBooks ska stoppa eller varna tydligt om perioden har:

- obalanserade verifikat
- differens i balansrapport eller saldobalans
- kritiska verifikationspunkter
- kritiska momspunkter
- momsperioder utan komplett beviskedja
- differens i bokforingskedjans integritetskontroll
- bokforingsrader utan tydlig kallkoppling
- kritiska bankavstamningspunkter
- fakturautkast i perioden
- kostnader utan kvitto eller underlag
- verifikat utan attest, vantande attest eller blockerad attest
- ovantade kontotecken pa viktiga konton

## Saker som ska synas som varningar

AliBooks ska ocksa visa varningar for:

- verifikationsvarningar
- momsvarningar
- SIE-export som inte ar redo
- bankavstamningsvarningar
- oppna kundfordringar
- forfallna kundfordringar
- oppna leverantorsskulder
- forfallna leverantorsskulder
- sena bokforingar, till exempel verifikat bokforda mer an 35 dagar efter verifikationsdatum

Varningar betyder inte alltid att perioden ar fel, men de ska granskas innan perioden anvands som professionellt underlag.

## Bevis som ska sparas

Vid periodlasning ska AliBooks kunna exportera:

- periodlasningskontroll
- periodstampel
- slutlig kedjekod
- antal verifikat
- blockerare och varningar
- sena verifikat
- bankdifferens
- atteststatus
- SIE-redo-status

Detta ar viktigt for att kunna forklara vad som kontrollerades vid stangning.

## Kommandot som skyddar funktionen

```bash
npm run check:period-close
```

Kontrollen verifierar att backend, frontend, tester, dokumentation, release gate och releasebevis fortfarande skyddar periodstangning.

## Stoppsignal

Stoppa bokslut, export eller skarp anvandning om:

- `npm run check:period-close` failar
- periodstangningskontrollen visar blockerare
- periodstampel eller slutlig kedjekod saknas efter lasning
- backup inte ar verifierad innan period stangs med riktig data
- GitHub Actions, backendtester eller release gate inte ar grona efter senaste andring
