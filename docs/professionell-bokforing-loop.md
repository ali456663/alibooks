# Professionell bokforingsloop for AliBooks

Den har loopen ar AliBooks arbetsordning nar systemet ska anvandas mer seriost.
Den ersatter inte redovisningskonsult eller juridisk radgivning, men den hjalper dig att kontrollera att viktiga delar ar pa plats innan rapport, moms, bokslut, molnflytt eller skarp drift.

Officiella utgangspunkter:

- Skatteverket: bokforingsskyldighet, lopande bokforing, moms och NE-bilaga for enskild naringsverksamhet.
  https://www.skatteverket.se/foretag/drivaforetag/foretagsformer/enskildnaringsverksamhet/bokforingochdeklaration.4.361dc8c15312eff6fd2c99f.html
- Skatteverket: fakturering och krav pa underlag.
  https://www.skatteverket.se/foretagochorganisationer/moms/saljavarorochtjanster/fakturering.4.58d555751259e4d66168000403.html
- Skatteverket: vad bokforingslagen kraver i praktiken.
  https://www.skatteverket.se/foretag/drivaforetag/bokforingochbokslut/bokforingvadkraverlagen.4.18e1b10334ebe8bc80005195.html
- Bolagsverket: aktiebolag ska lamna arsredovisning varje ar, normalt inom sju manader fran rakenskapsarets slut.
  https://bolagsverket.se/foretag/aktiebolag/startaaktiebolag/revisoriaktiebolag/vanligafragoromrevisoriaktiebolag.523.html

## 20 steg i ratt ordning

1. **Stoppa fel berakningar forst**
   Kontrollera moms, totaler, negativa saldon, delbetalningar, avrundning och differenser.
   AliBooks-vy: `Rapporter -> Berakningskontroll`

2. **Sakra debet och kredit**
   Alla verifikat ska balansera. Ratta med rattelseverifikat, inte genom att radera historik.
   AliBooks-vy: `Bokforing`

3. **Koppla underlag till affarshandelser**
   Fakturor, kvitton, PDF, betalreferenser och importer ska ga att hitta igen.
   AliBooks-vy: `Verifikationskontroll` och `Underlag`
   Aterkommande avtal arkiveras i backend istallet for att hardraderas, sa faktureringshistorik kan forstas i efterhand.

4. **Stam av bank, Stripe och delbetalningar**
   Kontrollera manuella betalningar, delbetalningar, overbetalningar, Stripe-fordran och bank-CSV.
   AliBooks-vy: `Betalningsavstamning`
   Om kunder betalar med kort, Apple Pay, Swish eller liknande elektronisk betalning ska du kontrollera kassaregisterregler eller kontantfaktura-rutin innan skarp drift.
   Automatiska betalningspaminnelser lases per faktura, sparar misslyckade forsok och kan aterforsoka en missad korning utan att skicka samma paminnelsetyp dubbelt.

5. **Stam av konton mot verkligheten**
   Jamfor 1930, 1510, 2440, 1580, moms och skattekonto mot externa underlag.
   AliBooks-vy: `Avstamning`

6. **Kontrollera moms, skatt och reserv**
   Moms att betala, skattekonto och reserv ska vara rimliga innan uttag, lon eller utdelning.
   AliBooks-vy: `Skattecenter` och `Momsrapport`

7. **Godkann verifikat och rattelser**
   Viktiga verifikat, sena bokningar och rattelser ska vara granskade.
   AliBooks-vy: `Internkontroll`

8. **Las period bara nar kontrollerna ar rena**
   Periodlasning ska komma efter avstamning, attest, underlag, moms och backup.
   AliBooks-vy: `Periodlasning` och `Bokslutscenter`
   Periodlasket kan bara flyttas framat. Om fel hittas i en last period ska rattelse ske som nytt verifikat i en senare period.

9. **Spara export och backup**
   Exportera SIE/CSV, rapporter, underlag och komplett backup innan stor andring.
   AliBooks-vy: `Arkivcenter`

10. **Ta beslut om skarp drift**
    Go-live ska inte vara en magkansla. Kritiska kontroller ska vara noll eller medvetet accepterade.
    AliBooks-vy: `Startklar`

11. **Kontrollera nummerordning**
    Fakturanummer och verifikationsnummer ska vara unika, lopande och begripliga.
    AliBooks-vy: `Nummerkontroll`

12. **Stada kund- och registerdata**
    Namn, e-post, personnummer, adress och bolagsuppgifter ska vara rimliga.
    AliBooks-vy: `Rapporter -> Datahalsa`

13. **Sakra ratt foretagsform**
   Enskild firma och aktiebolag anvander olika floden for skatt, eget kapital, lon och resultat.
   AliBooks-vy: `Rapporter` och `Installningar`
   Nar bokforingsrader finns stoppar backend byte av foretagsform och bokforingsmetod. Gor sadana byten som kontrollerad migration.

14. **Kontrollera flytt fran Bokio eller annat system**
    Importerade underlag, gamla fakturor, SIE/CSV och bankrader ska granskas innan AliBooks blir huvudsystem.
    AliBooks-vy: `Migreringscenter`

15. **Sakra arkiv och sparbarhet**
    Materialet ska kunna hittas, laddas ner och sparas over tid.
    AliBooks-vy: `Arkivcenter`
    Underlag/kvitton accepteras som PDF, JPG, PNG eller WebP, sparas med SHA-256-kod och laddas ner med sakra filnamn.
    Faktura-PDF, lonebesked/zip och dynamiska CSV-exporter anvander ocksa sakra `Content-Disposition`-filnamn.
    CSV-exporter neutraliserar formelstarter som `=`, `+`, `-` och `@` for att minska risk nar filer oppnas i Excel.
    Inloggning har temporar lasning efter upprepade fel. Styrs av `APP_AUTH_MAX_FAILED_LOGIN_ATTEMPTS`
    och `APP_AUTH_LOGIN_LOCK_MINUTES`, och visas i `Sakerhet`.
    JWT-sessionens livslangd styrs av `JWT_EXPIRATION_MINUTES`. Frontend loggar ut automatiskt nar sessionen gar ut
    och visar ett tydligt meddelande istallet for att lamna anvandaren i ett trasigt API-lage.
    Login, blockerade loginforsok och registreringar skrivs till revisionsspar.

16. **Skydda personuppgifter och AI-export**
    Skicka bara anonymiserade nyckeltal till AI. API-nycklar ska ligga i backend-miljo, inte frontend.
    AliBooks-vy: `Sakerhet`

17. **Bygg granskningsspar**
    Det ska ga att se vem som andrat, godkant, rattat, exporterat eller last period.
    AliBooks-vy: `Internkontroll`

18. **Sakra drift, miljo och release**
    Docker, GitHub Actions, backend, databas, backup och aterstallning ska vara tydliga.
    AliBooks-vy: `Driftcenter`
    CORS styrs av `APP_CORS_ALLOWED_ORIGINS` och `APP_CORS_LOCAL_DEV_ENABLED`.
    I produktion ska `APP_CORS_ALLOWED_ORIGINS` vara publik frontend-URL och `APP_CORS_LOCAL_DEV_ENABLED=false`.
    Backend satter sakerhetsheaders: `nosniff`, `DENY` for framing, `no-referrer` och restriktiv permissions-policy.
    Viktigt: `/test-data` ar avstangd som standard i backend. Satt bara `APP_TEST_DATA_RESET_ENABLED=true`
    i lokal testmiljo om du vill kunna rensa testdata. Lamna den avstangd i produktion.
    Bulk-rensning av bankavstamning ar ocksa avstangd som standard. Satt bara
    `APP_BANK_RECONCILIATION_RESET_ENABLED=true` i lokal testmiljo om du maste nollstalla importerade bankrader.

19. **Kor ett helt testflode**
    Testa kund, tjanst, faktura, PDF, e-post, betalning, bokforing, momsrapport och export tillsammans.
    AliBooks-vy: `Testflode`

20. **Ta sista riskbeslutet**
    Riskcenter ska vara gront eller ha tydliga accepterade risker innan du anvander appen skarpt.
    AliBooks-vy: `Riskcenter`

## Teknisk kvalitetsloop

Kor detta efter stor frontend-andring:

```bash
cd frontend
npm run build
npm run smoke:runtime
```

`smoke:runtime` oppnar AliBooks i riktig Chrome och failar om appen visar root-fallbacken `AliBooks kunde inte visa sidan`.
Det ar byggt for att fanga render-krascher som vanliga builden inte alltid ser.

## Vad som fortfarande kraver extra forsiktighet

- AliBooks kan hjalpa dig, men du ansvarar sjalv for korrekt bokforing.
- Loner, arbetsgivardeklaration, RUT/ROT, bankkoppling och verklig Skatteverket-inlamning bor verifieras extra innan skarp anvandning.
- Aktiebolag har andra krav an enskild firma, bland annat arsredovisning.
- Externa AI-verktyg ska inte fa personnummer, adress, telefon eller kundnamn om det inte ar absolut nodvandigt och lagligt hanterat.
