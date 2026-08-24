# AliBooks berakningskontroll

Det har ar kontrollkontraktet for de delar dar fel belopp kan gora stor skada: fakturor, moms, betalningar, Stripe, leverantorer, lon och rapporter. Syftet ar att AliBooks ska stoppa misstankta berakningar tidigt och visa nar man maste granska manuellt.

## Grundregler

- Fakturans totalbelopp ska alltid vara `netto + moms`.
- En faktura far inte blanda positiva och negativa belopp. Kreditfakturor ska vara konsekvent negativa.
- Delbetalningar far inte bokforas med mer an kvarvarande belopp.
- Vid kontantmetoden ska moms pa delbetalningar avrundas sa sista delbetalningen tar kvarvarande moms.
- Verifikat ska balansera: total debet ska vara lika med total kredit.
- Manuella verifikat och ingaende balans far inte ha negativa debet- eller kreditrader.
- Periodlasning ska stoppa bokforing pa gamla datum efter deklarerad eller betald momsperiod.
- Momsrapport ska bygga pa `2611` utgaende moms och `2641` ingaende moms.
- Momsavrakning ska ga via `2650` och betalning via skattekonto/bank enligt vald rutin.
- Stripe-forsaljning ska bokas via `1580`, `3041` och `2611`.
- Stripe-utbetalning ska stamma av `1580`, bankkonto `1930` och avgifter `6570`.
- Leverantorsfakturor ska bokas mot kostnadskonto, `2641`, `2440` och vid betalning mot `1930`.
- Lon ar MVP-arbetsunderlag: bruttolon, preliminar skatt, nettolon och arbetsgivaravgift ska granskas innan riktig rapportering.

## Stoppsignaler

Stoppa och felsok innan du fortsatter om:

- resultat- eller balansrapport visar oforklarat negativt saldo,
- `1580 Fordran hos Stripe` inte stammer efter utbetalning,
- `2611`, `2641` eller `2650` visar ovantad riktning,
- en momsperiod ar lasst men appen tillater nya verifikat i perioden,
- ett verifikat visas som obalanserat,
- Stripe eller bankimport skapar dubbel bokforing med samma referens,
- en betalning blir storre an kvar att betala,
- en CSV-export eller rapport inte gar att stamma av mot huvudbok.

## Bevis innan riktig anvandning

Kor:

```bash
npm run check:calculations
npm run test:backend
npm run check:release
```

Granska manuellt:

- en vanlig faktura med 25 procent moms,
- en delbetalning och slutbetalning,
- en kreditfaktura eller aterbetalning,
- en kostnad med kvitto och ingaende moms,
- en Stripe-forsaljning och Stripe-utbetalning,
- momsrapport och balansrapport efter bokforing.

Den har kontrollen ska lasa tillsammans med [anvanda-idag-beslut.md](anvanda-idag-beslut.md) och [professionell-bokforing-loop.md](professionell-bokforing-loop.md).
