# AliBooks berakningskontroll

Det har ar kontrollkontraktet for de delar dar fel belopp kan gora stor skada: fakturor, moms, betalningar, Stripe, leverantorer, lon och rapporter. Syftet ar att AliBooks ska stoppa misstankta berakningar tidigt och visa nar man maste granska manuellt.

## Grundregler

- Fakturans totalbelopp ska alltid vara `netto + moms`.
- En faktura far inte blanda positiva och negativa belopp. Kreditfakturor ska vara konsekvent negativa.
- Delbetalningar far inte bokforas med mer an kvarvarande belopp.
- Avtalsfakturor far bara skapas pa eller efter avtalets nasta fakturadatum; backend ska stoppa for tidig fakturering aven om UI-kontrollen kringgas.
- Vid kontantmetoden ska moms pa delbetalningar avrundas sa sista delbetalningen tar kvarvarande moms.
- Verifikat ska balansera: total debet ska vara lika med total kredit.
- Manuella verifikat och ingaende balans far inte ha negativa debet- eller kreditrader.
- Periodlasning ska stoppa bokforing pa gamla datum efter deklarerad eller betald momsperiod.
- Momsrapport ska bygga pa utgaende moms `2611`/`2621`/`2631` for 25/12/6 procent och `2641` ingaende moms.
- Momsavstamning jamfor varje stodjat forsaljningskonto (`3041`/`3042`/`3043`) med motsvarande momskonto; oklassificerade `3xxx`-konton ska granskas och blockerar momsavrakning tills de ar klassificerade.
- Forvantad utgaende moms avrundas en gang pa det aggregerade beskattningsunderlaget per momssats. Enskilda sma verifikationsrader avrundas inte var for sig, eftersom det annars kan ge en falsk differens.
- Momsavrakning ska ga via `2650` och betalning via skattekonto/bank enligt vald rutin.
- Stripe-forsaljning ska bokas via `1580` och forsaljnings-/momskonton som motsvarar den uttryckligen valda momssatsen.
- Stripe-utbetalning ska stamma av `1580`, bankkonto `1930` och avgifter `6570`.
- Manuella kund- och leverantorsbetalningar ska ha en enkel betalreferens; bankimport anvander bankradens unika ID. Utan referens kan en retry inte sakert skiljas fran en ny betalning.
- En momsperiod med belopp att betala eller återfå ska också ha bank- eller skattekontoreferens när den markeras som betald.
- Leverantorsfakturor ska bokas mot kostnadskonto, `2641`, `2440` och vid betalning mot `1930`.
- Lon ar MVP-arbetsunderlag: bruttolon, preliminar skatt, nettolon och arbetsgivaravgift ska granskas innan riktig rapportering.
- Lonebesked som exporteras eller skickas ska ha period i `YYYY-MM`-format, bruttolon over noll, skatt mellan noll och bruttolon, nettolon lika med `bruttolon - skatt` och total kostnad lika med `bruttolon + arbetsgivaravgift`.
- Lonearkiv far bara innehalla lonebesked for arkivets angivna period; textfalt i CSV-manifestet skyddas mot kalkylbladsformler.

## Stoppsignaler

Stoppa och felsok innan du fortsatter om:

- resultat- eller balansrapport visar oforklarat negativt saldo,
- `1580 Fordran hos Stripe` inte stammer efter utbetalning,
- `2611`, `2621`, `2631`, `2641` eller `2650` visar ovantad riktning,
- en momsperiod ar lasst men appen tillater nya verifikat i perioden,
- ett verifikat visas som obalanserat,
- Stripe eller bankimport skapar dubbel bokforing med samma referens,
- en betalning blir storre an kvar att betala,
- ett lonebesked har inkonsekvent skatt, nettolon eller total lonekostnad,
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
- ett lonebesked med PDF-export och kontrollerad bokforingssammanstallning,
- momsrapport och balansrapport efter bokforing.

Den har kontrollen ska lasa tillsammans med [anvanda-idag-beslut.md](anvanda-idag-beslut.md) och [professionell-bokforing-loop.md](professionell-bokforing-loop.md).
