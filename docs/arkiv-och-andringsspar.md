# Arkiv och andringsspar

AliBooks ska behandla bokforingsdata som historik, inte som vanliga app-poster.
Malet ar att du ska kunna rattas, exportera och forklara vad som hant utan att tappa sparbarhet.

## Grundregel

- Fakturor, verifikat, kvitton, betalningar, momsunderlag och rapporter ska bevaras.
- Fel ska normalt rattas med status, makulering, kreditfaktura eller rattelseverifikat.
- Hard delete ska bara vara tillatet for utkast eller lokal testdata som uttryckligen ar skyddad.
- Viktiga andringar ska skrivas till revisionsspar.
- Periodlasta datum ska stoppa andringar som skulle skriva om historiken.

## AliBooks-regler

| Omrade | Regel |
| --- | --- |
| Kund | Kund med fakturor raderas inte, den arkiveras. |
| Faktura | Bara fakturautkast far raderas. Skickad, betald eller bokford faktura ska krediteras/rattas. |
| Leverantorsfaktura | Betald eller bokford leverantorsfaktura far inte raderas. Anvand makulering eller korrigering. |
| Kvitto | Uppladdat kvitto ska inte ersattas. Skapa ny korrigering eller nytt underlag. |
| Bankimport | Bulk-reset ar avstangd som standard och kraver feature flag plus bekraftelseheader. |
| Periodlasning | Periodlasning far bara flyttas framat. Fel i last period rattas i senare period. |
| Export | CSV/SIE/PDF/arkiv ska kunna laddas ner och sparas sakert. |

## Go-live-regel

Fore skarp anvandning ska `npm run check:retention` och `npm run check:release:full`
vara grona. Efter push ska aven GitHub Actions vara gron och `npm run check:sync`
visa att lokal kod och GitHub ar i sync.
