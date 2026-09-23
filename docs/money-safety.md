# Beloppssakerhet och kvarvarande ore-migrering

## Status 2026-09-09

AliBooks ar fortfarande INTE godkant som enda system for skarp bokforing.
Databasens beloppsfalt, flera API-kontrakt och rapporter anvander heltal i SEK.
Detta steg andrar INTE enheten i lagrade belopp och andrar ingen historisk bokforing.

## Verifierad skuggmigrering 2026-09-21

Den dagliga användningsgaten behandlar nu saknat fullständigt örestöd som en
stoppande punkt för riktig bokföring, inte som en vanlig varning. Isolerad
testdata är fortfarande tillåten, men ett grönt lokalt test får inte tolkas som
ett godkännande att lägga in verkliga bokföringsposter.

Startup verifierar nu den additiva migreringen innan backend blir frisk. Om en
farsk, lyckad nattlig helkontroll finns anvands en snabb checkpoint; annars
kors en full kontroll innan backend blir frisk:

- alla definierade legacy/minor-par maste matcha exakt enligt valutans minor-unit-faktor;
- valutakoden maste vara `SEK`;
- varje verifikat maste balansera i minor units;
- kontoaggregat maste matcha mellan legacy- och minor-falten;
- avvikelser sparas radvis i `money_migration_issues`;
- godkand kontroll sparas i `money_migration_ledger` och exponeras i det autentiserade `/system/status`.

Nya och uppdaterade rader garanteras dessutom av PostgreSQL-triggers som skriver
minor-shadowen fran legacybeloppet med explicit `ROUND(..., 0)`. En omgaende
skrivning av bara legacy- eller bara minor-faltet kan darfor inte lamna ett
inkonsekvent par. Valutans exponent ligger i tabellen `currencies`; aktuell
verifiering ar SEK med exponent 2. Full kontroll kor aven schemalagt varje natt
med `APP_MONEY_MIGRATION_FULL_CHECK_CRON`. En checkpoint blir for gammal efter
`APP_MONEY_MIGRATION_MAX_FULL_CHECK_AGE_HOURS` timmar och da blockeras inte
kontrollen genom att hoppa over en ny full korning.

Checkpointen bevisar att den senaste fulla kontrollen var felfri och att alla
runtime-triggers fortfarande finns. Den ersatter inte nattens fulla kontroll.

Backendstatus visar dessutom cutover-bevisen som en separat fail-closed grind:
antal sammanhangande felfria helkontroller av sju, om en verifierad backup/restore
repetition finns och om API-/bokforingsfloden ar minor-unit-auktoritativa. Dessa
tre signaler maste alla vara uppfyllda innan `readyForAuthoritativeCutover` kan
bli sant. `APP_MONEY_MIGRATION_AUTHORITATIVE` ar fortsatt `false` i bada env-mallarna
och far inte slas pa bara for att skuggkontrollen ar gron.

Varje kontrollkorning far ett eget `run_id`. Avvikelser i
`money_migration_issues` ar append-only sa att nya avvikelser kan skiljas fran
historiska och granskas over tid. Om kontrollen inte kan genomforas markeras
status som blockerad och produktionskonfigurationen tillater inte att
startup-verifieringen stangs av.

Detta ar ett verifierat databasunderlag for nasta cutover, inte ett produktionsgodkannande.
Legacy-kolumnerna ar fortfarande auktoritativa och API-kontrakt, entiteter, importer,
rapporter och betalningsfloden ar inte fullt migrerade till minor units annu. Droppa
darfor inte gamla kolumner och lagg inte in riktig bokforingsdata innan den fullstandiga
cutovern, backup/restore-repetition och extern bokforingskontroll ar godkanda.

## Exakt API-kontrakt i steg 2

Faktura-, tjanste-, betalnings-, verifikations-, kostnads-, kortkop-, bankavstamnings-,
leverantorsfaktura-, Stripe-utbetalnings- och momsunderlagssvar exponerar nu dessutom
`*Minor`-falt och `currencyCode`, till exempel `totalAmountMinor: 125050` och
`currencyCode: "SEK"`. De gamla kronfalten finns kvar for kompatibilitet med den
befintliga klienten, men nya klientfloden ska lasa minor-faltet for berakningar och
visa valutakoden i stallet for att sjalva multiplicera ett kronbelopp med 100.

Detta kontrakt ar nu testat for alla dessa kärnobjekt i
`MoneyApiContractTest`. Ett synligt minor-falt betyder dock inte att objektet far
skrivas med ore ännu; backendens bokforings- och rapportfloden stoppar fortfarande
ore dar den gamla heltalsmodellen inte kan bevara beloppet exakt.

Detta ar ett API-forberedande steg, inte ett cutover: skrivvagarna och den gamla
lagringen ar fortfarande kronauktoritativa, och systemet stoppar fortfarande ett
orebelopp innan det kan bokforas genom den gamla kronbaserade journalmodellen.

## Uttradeskriterier for auktoritativ minor-unit-modell

Minor-unit-kolumnerna far bli auktoritativa for produktion forst nar alla punkter
nedan ar uppfyllda och dokumenterade:

1. Sju fullstandiga kontrollkorningar i rad har `issue_count = 0` och inga
   kontrollkorningar saknas i perioden.
2. En restore-repetition fran verifierad backup ar genomford och samma kontroller
   passerar efter restore.
3. Alla skrivvagar for belopp gar via minor units med vald valutakod och samma
   avrundningsregel; legacy-falt ar inte langre en separat skrivauktoritet.
4. API, entiteter, rapporter, importer, betalningar, moms och exporter anvander
   minor units utan att konvertera tillbaka till flyttal eller kapa oren.
5. Hela testsviten, periodkontrollerna och en manuell bokforingsgranskning ar
   godkanda. Forst darefter planeras borttagning av legacy-kolumner i en separat
   migrering.

## Rattat

## Skuggmigrering 2026-09-20

AliBooks har nu en versionssatt, additiv skuggmigrering for karndomänen faktura,
leverantorsfaktura, kostnad, kortkop, bankavstamning, Stripe-utbetalning, momsunderlag
och betalning. Nya
`bigint`-kolumner for produktpriser, kund- och leverantorsfakturabelopp, kostnader,
kortkop, bankrader samt
betalningsbelopp fylls exakt fran gamla hela kronor ganger 100. En enkel
`money_migration_ledger` sparar att `core-invoice-shadow-v1` ar applicerad.

De gamla heltalskolumnerna ar fortfarande de som applikationen anvander. Detta
ar darfor en forberedelse och en verifierbar kopia, inte fardigt ore-stod.
Nasta steg ar att migrera entiteter, API-kontrakt, rapporter, importer,
betalningar och historisk avstamning i separata kompatibla steg. Fakturans PDF
och CSV-export laser nu minor-unit-skuggor for visning och export, men den
befintliga rapportrevisionen stoppar fortfarande oren som den inte kan visa.
Stripe-utbetalningar och momsunderlag validerar nu sin skuggkopia vid lasning
och synkroniserar den vid sparning. En avvikande redan lagrad skuggkopia ger
ett stopp i stallet for att systemet raknar vidare pa tyst korrupt data.

Kundfakturans kvar-att-betala, betalnings- och aterbetalningslogik anvander nu
minor-unit-varden internt och stoppar overbetalning och overaterbetalning.
Eftersom den stegvisa migreringen fortfarande har gamla heltalskolumner som
auktoritativa synkroniseras fakturans skuggfalt fran legacyvardet vid lasning.
Det gor aldre importer och kontrollerade avstamningskorrigeringar deterministiska
tills fakturaflodet kan migreras helt.

Kundreskontrans historik och saldokontroll använder nu samma minor-unit-modell.
Betalningar summeras exakt och rapportens äldre heltalsfält stoppar belopp med
ören med HTTP 422 i stället för att avrunda bort differensen. Detta skyddar
rapporten under migreringen men ersätter inte den kvarvarande fullständiga
öre-migreringen av lagrade belopp och rapportkontrakt.

Leverantörsreskontran använder samma kontroll för betalningshistorik, netto,
moms, total och kvarvarande saldo. Ett öresbelopp stoppas före rapporten i
stället för att bli en annan leverantörsskuld i ett heltalsfält.

Momsrapporten och momsarkivet läser nu huvudbokens minor-unit-skuggor och
exporterar uttryckliga minor-kolumner. Ett öresbelopp stoppas vid den gamla
kronrapportens gräns i stället för att avrundas bort. Den fullständiga
migreringen av momsens API- och lagringsmodell återstår fortfarande.

Periodstängningens kontroll av senbokförda verifikat och reskontrans
huvudbokskontroll läser också minor-unit-skuggorna. Öresdifferenser stoppas
med HTTP 422 före periodlåsning eller ett falskt godkänt avstämningsresultat.
De kompatibla rapportfälten är fortfarande hela kronor tills den fullständiga
migreringen av API-kontrakt och lagring är genomförd.

Huvudbok och saldobalans använder nu samma minor-unit-kontroll för ingående
saldo, periodrörelser, löpande saldo och utgående saldo. Ett öresbelopp kan
därför inte längre passera som ett avrundat rapportvärde i dessa centrala
rapporter.

Leverantorsfakturans kvar-att-betala och betalningsflode anvander nu samma
minor-unit-kontroll. En betalning som skulle overskrida fakturans total stoppas
fore historik och bokforing uppdateras. Legacy-kolumnerna ar fortsatt
auktoritativa under migreringen och skuggfalt synkroniseras vid lasning.

Huvudbokens debet- och kreditrader har nu egna minor-unit-skuggfält och
verifikationens integritetshash binder även dessa exakta värden. De befintliga
  hela-kronorna är fortfarande auktoritativa; detta steg gör precisionen spårbar
  men är inte ett färdigt godkännande av hela öre-migreringen.

Revisionsspårets kedjehash och SIE-exportens kontrollsummering använder nu
minor-unit-skuggorna. SIE-exporten stoppas med HTTP 422 om ett verifikat
innehåller ören som den nuvarande kronbaserade exportformen inte kan bära.
Detta förhindrar att revisionsbevis eller redovisningspaket visar ett annat
belopp än verifikatets exakta värde, men den fullständiga öre-migreringen
återstår fortfarande.

Stripe Checkout skickar nu fakturans minor-unit-saldo direkt till Stripe.
Om saldot innehåller ören stoppas Checkout med HTTP 422 innan en betalningssession
skapas. Det hindrar att Stripe och AliBooks får olika belopp under den stegvisa
migreringen.

Journal-CSV-exporten läser också minor-unit-skuggorna. Den innehåller både
kompatibla kronfält och explicita minor-fält, men stoppas med HTTP 422 om en
verifikationsrad innehåller ören som den äldre kronrepresentationen inte kan
visa korrekt.

Bankavstämningen jämför nu bankradens och konto 1930-radens minor-unit-skuggor,
inte bara de gamla heltalsvärdena. Om en avstämningsrapport innehåller ören som
dagens rapportkontrakt inte kan visa stoppas rapporten med ett tydligt fel i
stället för att beloppet avrundas tyst.

- Bankimportens betalning/kostnad och historik sparas atomiskt. Hela bankbeloppet
  maste matcha bokningen; overbetalning kapas inte till restsaldot. Se
  [bank-import-atomicity.md](bank-import-atomicity.md) for dubblettskyddets avgransning.
- Bank-CSV och Bokio/SIE analyserar belopp i exakta oresheltal. Bankfil med oren
  avvisas i sin helhet innan den blir bokforingsbar. SIE-forhandsvisning bevarar
  oren, men bokforingsknappen stoppar belopp som heltals-SEK-modellen inte klarar.
  Detta ar fortfarande INTE fullt ore-stod.
- Bankimport gissar inte langre belopp fran sista kolumnen eller fran saldo nar
  ett uttryckligt transaktionsbelopp ar noll. Tvetydiga kolumner, ogiltiga belopp,
  trasiga citattecken och fel kolumnantal avvisas. Negativa utbetalningar bevaras.
- En SIE-differens pa exakt 0,01 kr raknas som obalans. Gamla cachade analyser
  maste lasas om; redan avrundade rader kan inte importeras genom den gamla vyn.
  Alternativa transaktionstyper kraver manuell hantering, inte vanlig automatisk import.
  Den begransade analysen ar inte en fullstandig SIE-importmotor.


- Momsfordelning for kunddelbetalning, aterbetalning och leverantorsdelbetalning
  anvander nu 64-bitars mellanprodukter och exakt division, inte int-multiplikation
  eller float. Fakturans total, moms och tidigare betalt valideras dessutom fran
  minor-unit-skuggor; ett orebelopp stoppas med HTTP 422 innan kontantmetodens
  betalningsverifikation sparas. Sista delbetalningen tar den kvarvarande
  avrundningsdifferensen.
- Kund-, leverantors- och kreditfakturans journalrader validerar total, netto
  och moms fran minor-unit-skuggor innan verifikationsnummer eller journalrader
  skapas. Ett orebelopp stoppas med HTTP 422 i stallet for att det gamla
  kronofaltet bokfors.
- Den nattliga migreringskontrollen blockerar ocksa asymmetriska historiska rader:
  en rad dar bara legacy-beloppet eller bara minor-unit-skuggan finns ar ett
  dataproblem och sparas med eget avvikelse-id i den append-only kontrollhistoriken.
- Exempel pa tidigare felrisk: 25 000 kr moms multiplicerat med 100 000 kr betalt
  overskrider int-gransen redan innan divisionen med fakturatotalen.
- Fakturapris multiplicerat med antal och netto plus moms kontrolleras for overflow.
  Ogiltiga nya fakturor avvisas fore sparning, i stallet for att fa ett negativt belopp.
- Avtalspris ganger antal valideras innan avtal och audit sparas. Fakturaexportens
  auditsumma beraknas i long och avvisas med REPORT_AMOUNT_LIMIT om den inte ryms.
- PDF skiljer total, betalt och kvarvarande belopp. Kredit och utkast markeras
  utan vanlig betalningsbegaran. Se [fakturadokument](invoice-document-snapshots.md).
- Kreditutkast anvander originalfakturans sparade belopp och rabatter, oberoende av
  tjanstens nuvarande pris. En prishojning kan inte blockera eller prissatta om krediten.
- Kostnads-API:t avvisar negativ moms och totalsummor over int-gransen.
  Kostnadsformular visar fel innan anrop for ogiltiga/fractionella belopp.
- Jackson kapar inte langre decimaltal till int/long i inkommande JSON. Ett
  decimaltal till ett heltalsfalt ger HTTP 400. Decimaltyper, dar de redan finns,
  paverkas inte av denna konfigurering.
- Fler-radiga manuella verifikationer och ingangsbalanser summeras i long fore
  balanskontrollen. Ett totalt debet pa 4 294 967 396 kan inte langre misstas for 100.
  Enskilda verifikationer over den nuvarande rapportmodellens int-grans avvisas.
- Resultat, balans, moms, saldobalans, huvudbok, kontotecken, verifikationskontroll,
  integritetsrapport och SIE-exportkvitto summerar nu med long-mellanvarden.
  Belopp som inte ryms i befintliga rapportfalt stoppas med HTTP 422 och
  felkoden REPORT_AMOUNT_LIMIT. Aven negativa gransvarden och differenser provas.
- Periodlasning och SIE-balanskontroll jamfor long-summor. Obalans kan inte doljas
  genom att debet/kredit slar runt vid int-gransen.
- Samtidiga rattelseforsok laser originalverifikationens rader i samma transaktion.
  PostgreSQL-test verifierar att parallella anrop inte kan skapa tva rattelser for
  samma original.
- Bankavstamning och kund-/leverantorsreskontra har samma long-summering och
  rapportgrans. Skydden omfattar aven bucket-summor och leverantorsexportens total.
  Se [leverantorsbetalningar](supplier-payment-safety.md) for samtidighetskontroll,
  periodregler och kvarvarande begransningar i historiska saldon.
- Frontend accepterar inte langre serverfel som resultat-, balans-, moms- eller
  saldobalansrapport. Misslyckad laddning visar fel och behaller inte gammal rapport.
  Giltiga negativa resultat bevaras; ett minus ar inte automatiskt ett berakningsfel.
- Frontendens formulär for leverantorsfakturor, betalningar, anlaggningstillgangar,
  eget kapital, kortkop och periodiseringar rundar inte langre decimalinput. Nar
  backendens modell fortfarande kraver hela kronor stoppas till exempel `1250,50`
  vid formulargransen i stallet for att tyst andras till `1251`.

## Kvar som blockerar skarp anvandning

Momsbevisets verifikationssummering använder nu samma minor-unit-värden som
momsavstämningen och periodstängningen. Ett äldre heltalsfält kan därför inte
längre dölja ett öresbelopp i settlement- eller payment-voucherns beviskedja;
rapporten stoppar med HTTP 422 innan ett felaktigt momsbevis används.

Periodstängningens första verifikatbalanskontroll använder också minor-unit-
värden och overflow-säker summering. Ett verifikat som ser balanserat ut i
gamla hela kronor men skiljer sig i ören blir därför stoppat innan perioden kan
låsas.

Momsbetalningar med ett belopp att betala eller återfå kräver nu en icke-tom
bank- eller skattekontoreferens. Det hindrar en betald momsperiod från att skapa
en betalningsverifikation utan ett användbart revisionsspår.

Datumkontroll: kundbetalningar, återbetalningar, kostnader och leverantörshändelser kräver nu uttryckligt bokföringsdatum. Detta hindrar felaktig placering i dagens period, men ersätter inte historisk avstämning.

1. **Fullt ore-stod:** Skatteverkets rattliga vagledning sager att ersattning,
   beskattningsunderlag och moms i en faktura ska anges med kronor och oren nar
   beloppen innehaller oren. Beraknad fakturamoms med oren far inte avrundas till
   hela kronor. Momsdeklarationens hela-kronorsavrundning ar en separat regel.
   Se [berakning av moms vid oresavrundning](https://www4.skatteverket.se/rattsligvagledning/edition/2026.12/381778.html),
   [avrundning i faktura](https://www4.skatteverket.se/rattsligvagledning/edition/2023.16/321578.html)
   och [belopp i momsdeklarationen](https://www4.skatteverket.se/rattsligvagledning/edition/2025.5/410881.html).
   Databas, API, fakturor, kvitton, moms, importer, Stripe,
   aterbetalningar och exporter maste behalla samma exakta belopp hela vagen.
Kundfakturans centrala berakning avrundar inte langre moms till hela kronor. Om
exakt moms innehaller oren som den nuvarande kronbaserade faktura- och
bokforingsmodellen inte kan bevara stoppas fakturan fore sparning och utskick
med ett tydligt fel. Det ar sakrare an att skapa en faktura med fel total, men
det ar fortfarande inte en losning for verkliga underlag med oren.
2. **Bredare beloppsmodell:** skydden ovan stoppar for stora rapportsummor, men
   utokar inte rapporternas kapacitet eller ger ore-stod. Frontendens egna analyser
   och ovriga beloppsfloden maste granskas separat. Historisk reskontra och
   transaktionsvis bankmatchning ar inte fardigverifierade.
   Rapportsystemet ska migreras samlat innan gransen kan tas bort.
3. **Historisk avstamning:** redan bokford moms kan vara fel fran den tidigare
   berakningen. Jamfor mot underlagen; inga historiska poster skrivs over av denna fix.
   Eventuella rattelser maste goras genom granskat rattelseflode.
4. **Momsregler:** nya fakturor och manuella Stripe-forsaljningar kan anvanda 6, 12
   eller 25 procent, valt per tjanst eller forsaljning. Detta ar ett avgransat stod,
   inte en generell momsmotor: systemet klassificerar inte automatiskt vad en vara
   eller tjanst ska ha for momssats och stodjer inte andra skattesituationer.
   Verifiera varje tjanst och historisk bokforing separat. Momsbelopp avrundas
   fortfarande till hela kronor; se [momssatsstod](vat-rates.md).
5. **Verklig backup och verksamhetsprov:** [backup-runbook](backup-restore-runbook.md)
   samt [Stripe-kontrakt](stripe-booking-safety.md) har separata kvarvarande krav.

## Krav pa nasta migrering

- Valj en enhet och precisionsmodell uttryckligen; byt aldrig innebord av befintliga
  heltalsfalt utan versionssatt migrering och verifierad aterlasningsbar backup.
- Prova gamla fakturor, krediter, delbetalningar, importer och oppna saldon i en kopia.
- Anvand exakta decimaler eller heltal i minsta valutaenhet, aven i summeringar.
- Testa 0,01 kr, belopp med 6/12/25 procent, rabatt, delbetalning och sista
  avrundningsdifferens genom databas, bokforing, PDF/export och betalprovider.
- Jamfor rapporter och ingangsbalanser fore/efter. Godkann inte bara en gron build.

Momsarkivets CSV-export validerar dessutom varje rad fran minor-unit-vardet innan
de kompatibla kronfalten skrivs. Ett orebelopp stoppas med HTTP 422 aven om flera
rader tillsammans skulle summera till en hel krona.
