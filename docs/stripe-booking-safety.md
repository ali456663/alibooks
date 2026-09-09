# Stripe-bokforing: avgransning och kontroller

AliBooks ar inte intygat klart for skarp bokforing. Grona tekniska tester ersatter
inte avstamning av verkliga underlag, belopp, moms och backup/aterlasning.

## Automatiskt flode

- Signaturen verifieras mot den ororda webhook-bodyn innan databasarbete.
- Endast engangsbetalningar med `payment_status=paid` bokfors.
- `checkout.session.completed` med `unpaid` vantar pa den separata
  `checkout.session.async_payment_succeeded`-handelsen.
- `amount_total` maste vara positivt, inom heltalsgransen och i SEK. Belopp med
  oren nekas; de avrundas aldrig tyst.
- Fakturan identifieras endast med metadata `invoiceId`, som AliBooks satter vid
  Checkout-skapandet. `client_reference_id` far inte ensam koppla ett webbkop till
  en faktura: faltet kan innehalla ett kundnummer eller annan extern referens.
- Fakturan maste vara skickad eller delbetald. Belopp storre an restsaldot nekas
  for manuell avstamning; de krymps inte till restsaldot.
- Betalningen bokfors mot 1580 enligt befintligt Stripe-avrakningsflode.
  Bankkontot paverkas forst genom separat avstamning/bokning av utbetalningen.
- Momsuppgifterna kommer fran den befintliga fakturan. Webbbetalningar utan
  fakturakoppling nekas nu i stallet for att automatiskt anta 25 procent moms.
- Handelsedatum utgar fran eventets skapandetid i Europe/Stockholm, inte dagen
  da ett aterforsok tas emot. Last bokforingsperiod maste hanteras manuellt.

## Dubbletter och transaktioner

PostgreSQL advisory transaction locks serialiserar event-ID och session-ID.
Fakturaraden lases med samma FOR UPDATE som manuella betalningar.
Eventtabellen innehaller bade riktiga `evt_...` och interna `checkout:cs_...`-nycklar
med typen `checkout.session.booked`. Ingen ny tabell eller migration kravs.
Markorer, fakturastatus, betalningshistorik och verifikat sparas i samma transaktion.
Databasfel propagerar som serverfel och lamnar inte en felaktig behandlad-markor.

En Checkout-URL skapas fortfarande med befintlig SDK-version for att halla
denna korrigering avgransad. Klienten anvander inte langre global API-nyckel.
Uppdatering av sparat Checkout-ID uppdaterar bara detta falt, inte en gammal
kopia av fakturans betalsaldo. SDK/API-version ska granskas separat fore produktion.

## Manuella blockerare fore skarp anvandning

1. Prova hemsida -> Stripe testmode -> publik webhook -> faktura -> 1580 ->
   utbetalning/avgift -> bankavstamning med verkligt testunderlag.
2. Bygg genomgaende oren-stod innan decimalbelopp anvands. Nuvarande skydd nekar dem.
3. Avstam tidigare Stripe-bokningar som skapats fore hardningen. Aterspela inte
   gamla sessioner blint: de saknar de nya sessionsmarkorerna.
4. Sakra momsdata for webbkop som inte redan har en AliBooks-faktura.
5. Hantera aterbetalningar, tvister och e-postskrivares samtidighet separat.
6. Prova aterlasning av riktig databas OCH kvittofiler till en separat miljo.
7. Kontrollera foretagsinstallningar, ingaende balanser och rapporter tillsammans
   med redovisningskunnig innan AliBooks ersatter befintlig bokforing.

Misslyckade webhook-anrop maste foljas upp i Stripe Workbench; det finns inte
en fardig lokal ko/avvikelseinkorg for dessa fall. Nekad bokforing betyder inte
att kundens betalning avbrutits eller aterbetalats.

## Kallor

- [Stripe: fulfill orders](https://docs.stripe.com/checkout/fulfillment)
- [Stripe: webhook signatures, retries and duplicates](https://docs.stripe.com/webhooks)
