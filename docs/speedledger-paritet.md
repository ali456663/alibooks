# AliBooks funktionsparitet mot SpeedLedger-liknande system

Den har matrisen anvands for att jamfora AliBooks med funktioner som ofta finns i svenska bokforingssystem, till exempel SpeedLedger: bokforing, fakturering, autokontering, bankkoppling, momsrapport, digitala underlag, balansrapport, resultatrapport, e-faktura, skattekonto, lon, NE-bilaga, arsredovisning och bokslutsstod.

Målet ar inte att kopiera ett annat system rakt av. Målet ar att AliBooks ska vara tydlig med vad som ar klart, vad som ar lokal MVP och vad som kraver extern integration innan skarp drift.

## Statusnivaer

- **Klar i AliBooks**: Funktionen finns i appen eller backend och ingar i release-kontroller.
- **Lokal MVP**: Funktionen finns som arbetsflode, kontroll eller export, men kraver manuell verifiering innan skarp anvandning.
- **Kraver extern integration**: Funktionen kan inte vara fullt klar utan bank, Skatteverket, PEPPOL, SMTP, Stripe, redovisningskonsult eller annan extern aktor.
- **Senare**: Bra produktsteg, men inte blockerande for lokal bokforings-MVP.

## Matris

| Omrade | AliBooks status | Nuvarande stod | Innan skarp drift |
| --- | --- | --- | --- |
| Bokforingsprogram | Klar i AliBooks | Bokforing, kontoplan, verifikat, SIE/CSV, periodlasning, rapporter och redovisningskontroll. | Kontrollera forsta skarpa perioden med redovisningskonsult om du ar osaker. |
| Faktureringsprogram | Klar i AliBooks | Kunder, tjanster, fakturor, PDF, betalning, delbetalning, paminnelser, offert och RUT/ROT-arbetsunderlag. | Testa PDF, e-post, betalning och bokforing med riktig testkund. |
| Autokontering | Lokal MVP | Bank-CSV, bankregler, matchning mot faktura/kostnad och bankavstamning. | Riktig bankkoppling/BankID eller Tink/GoCardless kraver separat integration. |
| Bankkoppling | Kraver extern integration | CSV-import och avstamning finns. | Automatisk hamtning fran bank kraver extern open-banking-leverantor och avtal. |
| Momsrapport | Klar i AliBooks | Momsrapport, momsavstamning, skatt/reservplan och export. | Jamfor perioder och belopp mot Skatteverket innan deklaration. |
| Digitala underlag | Klar i AliBooks | Underlag/kvitto/PDF, hash, sakra filnamn, export och saknade underlagskontroller. | Kontrollera att underlag gar att ladda ner och matcha innan periodlasning. |
| Balansrapport | Klar i AliBooks | Balansrapport, balansdiagnos och differenskontroller. | Balansdifferens ska normalt vara 0 innan rapport/bokslut. |
| Resultatrapport | Klar i AliBooks | Resultatrapport, resultatdiagnos, budget och kassaflodesstod. | Kontrollera konto 3xxx/4xxx/5xxx mot verkliga underlag. |
| E-faktura / PEPPOL | Kraver extern integration | Vanlig faktura-PDF och e-post finns. | PEPPOL/e-faktura kraver extern operator och avtal. |
| Skattekonto | Lokal MVP | Skattekonto-avstamning, konto 1630/2012, moms/skatt-reserv och PDF/CSV-underlag. | Jamfor alltid mot Skatteverkets skattekonto. |
| Lon | Lokal MVP | Lonesammanstallning, lonespecifikation, e-post, skatt/arbetsgivaravgift och kontrollunderlag. | Riktiga skattetabeller, AGI och avtal ska verifieras innan verklig lon. |
| Bokslut | Lokal MVP | Bokslutscenter, arsbokslut, periodiseringar, resultatverifikat och export. | Slutligt bokslut ska granskas innan inlamning eller arsavslut. |
| Arsredovisning | Kraver extern kontroll | AliBooks skapar arbetsunderlag for AB/K2 och resultatkonto 2099. | Komplett juridisk arsredovisning till Bolagsverket ar inte fullt automatiserad. |
| NE-bilaga | Lokal MVP | Enskild firma visar NE-kontrollunderlag och preliminar resultatgrund. | Skattemassiga justeringar, egenavgifter och rantefordelning maste granskas. |
| E-dagsavslut | Kraver extern integration | Daglig rutin, kassaflode och betalningsavstamning finns. | Dagsavslut fran kassa/POS kraver separat kassasystem eller import. |
| Salj faktura / factoring | Senare | Kundfordringar, forfallna fakturor och paminnelser finns. | Fakturakop/factoring kraver extern finanspartner och avtal. |
| Fullservice / radgivning | Kraver extern aktor | Redovisningspaket, kontrollfragor och export till konsult finns. | AliBooks ersatter inte redovisningskonsult eller juridisk radgivning. |

## MVP-beslut

For att AliBooks ska bli anvandningsklar snabbare ska vi prioritera:

1. Stabil bokforing, fakturering, underlag, moms och rapporter.
2. Bank-CSV, avstamning och autokontering-regler innan riktig bankkoppling.
3. Stripe/SMTP med testnycklar innan skarp betalning och e-post.
4. Redovisningspaket och export innan fullservice.
5. NE/arsbokslut som arbetsunderlag innan juridisk inlamning.

## Externa stoppunkter

Anvand inte dessa som skarpa funktioner innan extern verifiering finns:

- riktig bankkoppling med BankID/open banking
- e-faktura/PEPPOL
- komplett arsredovisning till Bolagsverket
- NE-bilaga som direkt inlamningsfil
- E-dagsavslut fran kassasystem
- Salj faktura/factoring
- fullservice/radgivning utan extern konsult

