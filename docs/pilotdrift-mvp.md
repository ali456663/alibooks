# AliBooks pilotdrift MVP

Pilotdrift betyder att AliBooks anvands forsiktigt med begransad riktig arbetsdata innan full produktion.

Det ar inte samma sak som skarp molndrift. Syftet ar att hitta fel tidigt, skydda bokforingen och kunna ga tillbaka om nagot blir fel.

## Pilotgrans

AliBooks kan ga in i pilotdrift nar detta ar sant:

- `npm run check:release` ar gron.
- `npm run check:first-real-data` ar gron.
- `npm run check:env-go-live` ar gron.
- `npm run check:calculations` ar gron.
- `npm run check:retention` ar gron.
- `npm run check:audit-integrity` ar gron.
- `npm run check:git -- --strict` ar gron efter senaste commit.
- Backup ar skapad och verifierad.
- Restore drill ar gjord i separat testdatabas.
- Testdata ar rensad eller tydligt separerad.
- Faktura-PDF, e-post, betalning, underlag och export ar manuellt klicktestade.

## Begransning for forsta veckan

Under forsta pilotveckan:

- anvand max 1-3 riktiga kunder
- skapa fa fakturor och kontrollera varje PDF innan den skickas
- registrera betalningar manuellt eller via tydligt testad Stripe-rutin
- importera bank-CSV forsiktigt och bokfor inte stora batchar utan export forst
- lagg in kvitton en i taget och kontrollera underlag i export
- gor daglig backup innan du stanger datorn

## Daglig pilotrutin

Varje dag:

1. Kor `npm run doctor`.
2. Oppna `Startklar` och kontrollera att inga kritiska punkter finns.
3. Oppna `Redovisningskontroll` och kontrollera obalanserade verifikat.
4. Oppna `Momsrapport` och kontrollera att momsbelopp verkar rimliga.
5. Oppna `Underlag` och kontrollera saknade kvitton/fakturor.
6. Exportera relevant CSV/PDF om du har bokfort riktig data.
7. Skapa och verifiera backup.

## Stoppa pilot direkt

Stoppa och felsok innan mer data laggs in om:

- frontend visar vit sida eller render recovery
- backend eller PostgreSQL inte svarar
- fakturanummer eller verifikationsnummer ser fel ut
- verifikat inte balanserar debet och kredit
- momsrapporten blir orimlig utan forklaring
- kunddata eller personnummer visas i anonym AI-export
- backup inte kan verifieras
- restore drill saknas
- e-post eller Stripe markeras som klart utan testbevis
- Git har ocommitade viktiga filer

## Efter pilotveckan

Efter forsta veckan:

- exportera resultatrapport, balansrapport, huvudbok, momsrapport och verifikationslista
- jamfor bank/Stripe/Swish mot AliBooks
- kontrollera kundreskontra och leverantorsskulder
- lamna redovisningspaket till konsult eller gor manuell rimlighetskontroll
- besluta om appen far anvandas bredare eller om stoppunkter ska fixas forst

## Kontrollkommando

```bash
npm run check:pilot
```

Las tillsammans med:

- [forsta-riktiga-data.md](forsta-riktiga-data.md)
- [anvanda-idag-beslut.md](anvanda-idag-beslut.md)
- [miljovariabler-go-live.md](miljovariabler-go-live.md)
- [backup-restore-runbook.md](backup-restore-runbook.md)
- [redovisningspaket-och-konsultexport.md](redovisningspaket-och-konsultexport.md)
