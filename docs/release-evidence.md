# AliBooks release evidence

## Inloggningsskydd mot resursmissbruk 2026-09-23

Misslyckade loginforsok har nu ett konfigurerbart cachetak och inaktiva poster
stadas bort. Nar taket nadts nekas nya forsoksnycklar utan att aktiva sparrar
tas bort. Produktions-Nginx begransar login till 30 anrop/minut per klient-IP
med kort burst, returnerar 429 vid overskridande och begransar bara login-body
till 16 KiB; kvitto- och dokumentuppladdningar behaller sin tidigare grans.

Verifierat lokalt: backend 497 tester, 0 fel pa Java 21; frontend release gate;
Docker-konfigurationsgrind; production frontend image build; `nginx -t` godkand.
GitHub Actions for den har andringen aterstar efter push. Detta ar inte go-live-
godkannande: extern WAF/rate limit, audit-retention, restore, molndrift,
integrationer och redovisningsgranskning aterstar.

## Produktionscontainrar och releasevalidering 2026-09-23

Produktionsimagesen har nu healthchecks for backend och frontend. EC2-deployen
kor `docker compose up -d --wait --wait-timeout 120` och startar inte publikt
smoke-test innan containrarna ar friska. Dockerhub-workflowen har dessutom en
separat releasevalidering som maste passera fore image-publicering.

Verifierat lokalt med `npm.cmd run check:release:full`:

- frontendbygge och runtime smoke: godkanda;
- backend: 495 tester, 0 fel;
- PostgreSQL-integration: 216 tester, 0 fel;
- backend- och frontend-production images: byggda utan fel;
- slutlig release gate: godkand.

Detta ar lokalt releasebevis. GitHub Actions, Dockerhub, EC2/RDS, extern
backup-restore, Stripe, SMTP och redovisningsgranskning maste fortfarande
verifieras externt fore riktig produktionsdata.

## Fakturamejl: bestandig leveranssparning 2026-09-23

Varje fakturamejl skapar nu ett separat leveransforsok i databasen fore SMTP.
Forsoket innehaller mottagare, rubrik, renderad text och kontrollsumma for
PDF-bilagan. Efter transport markeras det `SENT`; vid SMTP-fel eller timeout
markeras det `UNCERTAIN` och det ursprungliga felet kastas vidare. Sparningen
sker i en separat transaktion, sa posten finns kvar aven om den omgivande
fakturatransaktionen rullas tillbaka.

`GET /system/status` visar `email.uncertainDeliveryCount` efter autentisering.
Detta ar en revisions- och avstamningskontroll, inte ett bevis pa att SMTP ar
konfigurerad eller att en mottagare faktiskt har oppnat mejlet. Automatisk
outbox, kontrollerad aterforsokshantering och riktig SMTP-verifiering aterstar
fore skarp drift.

Riktade tester for leveransforsok, systemstatus och schema-patch passerade.
Schema-migreringen speglar nu **456/456** startup-SQL-steg.

## Kundbetalningars dubblettskydd 2026-09-23

Kundbetalningar skyddas nu av både fakturans radlåsning och en partiell unik
databasidentitet på faktura, betalningsdatum, belopp och referens. Om två
samtidiga anrop ändå försöker spara samma betalning stoppas det andra med HTTP
409 i stället för att skapa en extra betalningsrad eller revisionshändelse.

Riktade tester för `OrderController` och `DatabaseSchemaPatch` passerade efter
ändringen. Skyddet gäller identiska betalningar med referens; separata
betalningar utan referens eller med annan referens kan fortfarande registreras.

## Stripe-utbetalningars dubblettskydd 2026-09-23

Stripe-utbetalningar skyddas av en partiell unik databasidentitet på referensen.
Tjänsten flushar nu utbetalningen innan den lämnar betalningsflödet och översätter
en samtidig unikhetskonflikt till HTTP 409. Då skapas inte ett andra godkänt
utbetalningsflöde och klienten får ett tydligt svar i stället för ett sent
transaktionsfel.

Detta gäller referenser som inte är tomma. Utbetalningar utan referens tillåts
fortfarande, men bör användas sparsamt eftersom de inte kan idempotensskyddas
av referens.

## Strukturerade leverantorsbetalningar 2026-09-23

Nya leverantorsbetalningar sparas nu som separata, daterade rader med
referens, skapandetid, valuta och minor-unit-skugga i
`supplier_invoice_payments`. Den äldre textkolumnen finns kvar for export och
aldre importer, men nya rapporter anvander strukturerade rader nar de finns.
En databas-trigger synkroniserar minor-unit-skuggan aven om en skrivning sker
utanfor JPA.

Riktade backendtester for betalningsfloden, historisk reskontra och
schema-patch passerade. Schema-migreringen speglar nu
**438/438** startup-SQL-steg. Aldre textposter migreras inte automatiskt; de
maste fortfarande kontrolleras mot verkliga underlag innan skarp drift.

Hela backendsviten kordes efter andringen: **489 tester, 0 fel och 0 errors**.

Betalningsidentiteten skyddas dessutom med ett unikt databasuttryck och
flush-tidpunkt i statusflodet. En samtidig dubblett blir HTTP 409 och
bokforingsskrivningen rullas tillbaka.

Leverantorsfakturans API/UI visar nu strukturerade betalningsrader med
minor-unit-belopp och valuta; den gamla betalningstexten ar endast fallback for
aldre poster.

## Användningsgaten stoppar riktig bokföring utan örestöd 2026-09-21

Startklar visar nu den saknade minor-unit-migreringen som stoppande för riktig
bokföring. Avskild testdata är fortfarande tillåten. Detta ändrar inte lagrade
belopp eller färdiga bokföringsflöden; det gör bara lokalens beslut konsekvent
med `productionBookkeepingReady=false` och riskregistret.

Samma första-datagat stoppar nu också när verifierad backup/restore saknas.
Det följer go-live-riskregistret och innebär inte att en lokal syntetisk backup
är ett godkännande av användarens riktiga återställning.

## Momsbevis använder minor-unit-summor 2026-09-21

Momsens settlement- och payment-bevis summerar nu verifikatradernas exakta
minor-unit-värden i stället för de äldre hela-kronofälten. Ett öresbelopp som
tidigare kunde döljas i beviskedjan stoppas nu med HTTP 422 innan periodstängning
eller redovisningskontroll kan markera beviset som godkänt.

Periodstängningens voucherbalans använder nu samma minor-unit-källa. Ett äldre
kronfält kan därför inte längre markera ett verifikat som balanserat när exakta
debet- och kreditvärden skiljer sig.

Momsavstämningens förväntade utgående moms räknas nu på det aggregerade
beskattningsunderlaget per momssats och avrundas en gång. Flera små rader kan
därför inte längre skapa en falsk differens genom att avrundas var för sig.

Regressionstestet `rejectsVatFilingProofWhenVoucherShadowContainsOre` täcker
detta fall. Backendtestet kunde inte köras i den aktuella miljön eftersom varken
lokalt Maven eller Docker-motorn var tillgänglig; release gate och statiska
kontroller körs separat.

## Stripe- och bankimportkontroller verifierade 2026-09-21

Den lokala backendsviten passerade efter de nya fail-closed-kontrollerna med
**477 tester, 0 fel och 0 errors**. Testerna täcker både exakt Stripe-moms och
att bankimportens kostnadsförslag inte kan skapa en avrundad bokföringsrad.

## Frontend stoppar inkonsekvent moms före fakturaskapande 2026-09-21

Fakturaförhandsvisningen använder nu samma exakta hela-kronorsregel som
backend. Om valt pris och antal ger ören i moms visas ett tydligt stopp,
förhandsvisningen visar inget avrundat totalbelopp och knappen för att skapa
fakturan är avstängd. Offertskapande och servicejobbsfakturering använder samma
förkontroll. Det förhindrar att användaren först ser ett avrundat belopp och
sedan möter ett oväntat backendfel.

Frontendbygget, bundle-kontrollen och release gate passerade efter ändringen.

## Bankimport stoppar osäker momsuppdelning 2026-09-21

Kostnadsförslag från bankimport räknar nu baklänges från totalbeloppet med
exakt heltalsaritmetik. Om netto och moms inte kan representeras som hela
kronor stoppas bokföringen, knappen visar att underlaget måste kontrolleras
manuellt och ingen avrundad uppdelning skickas till backend.

## Stripe-försäljning stoppar osäker momsuppdelning 2026-09-21

Stripe-försäljning använder nu exakt heltalsaritmetik när ett totalbelopp
delas i netto och utgående moms. Om uppdelningen skulle kräva ören svarar
frontend och backend med ett tydligt stopp; frontend skickar inte ens
begäran och backend svarar dessutom med HTTP 400 innan verifikationsnummer
eller journalrader skapas.
Det skyddar 1580, försäljning och momskonto från en tyst avrundningsdifferens.

## Stripe-referens skyddar manuell försäljning 2026-09-21

Manuell Stripe-försäljning kräver nu en referens från Stripe. Backend låser
referensen i transaktionen och jämför den mot exakt verifikationsbeskrivning,
så närliggande referenser inte kan kollidera och samtidiga anrop inte kan
dubbelbokas. API-svaret filtrerar dessutom på exakt referens, så en äldre
liknande referens inte kan visas som den nya bokningen. Backendtesterna omfattar
detta skydd.

## Exakt momsberäkning före fakturautskick 2026-09-21

Den centrala kundfakturaberäkningen använder nu minor-unit-aritmetik för moms
innan beloppet konverteras till den äldre heltalsmodellen. Om exakt moms
innehåller ören som den nuvarande fakturamodellen inte kan lagra stoppas
fakturaskapandet med ett tydligt fel i stället för att beloppet tyst avrundas.
Detta är en säkerhetsgräns tills hela faktura-, journal-, rapport- och
exportmodellen är migrerad till ören; det är inte ett påstående om fullständigt
örestöd ännu.

Backendtesterna passerade med **473 tester, 0 fel och 0 errors**. Den
fullständiga integrationssviten passerade med **215 tester, 0 fel och 0 errors**.

## Beloppskontroller i fakturor, leverantör, kortköp och kostnader 2026-09-21

Fakturautskickets obligatoriska fältkontroll läser nu moms och total från
minor-unit-skuggorna och stoppar ören med HTTP 422 innan en faktura kan
utfärdas. Samma fail-closed-kontroll används innan en leverantörsfaktura
makuleras, så ett avvikande betalt belopp inte kan passera genom ett gammalt
kronfält.

Fakturaexporten validerar varje minor-unit-belopp innan någon kompatibilitetsrad
skrivs och exporterar endast hela kronor i de äldre kolumnerna. Därmed kan en
delvis byggd CSV inte innehålla avrundade belopp om exporten stoppas.

Kortköp som bokförs som kostnad validerar total, netto och moms innan någon
kostnad eller journalpost sparas. Kostnadens skapande, kvittoaudit och reparation
av kvittohash använder också minor-unit-totalen för auditspåret.

Backendtesterna passerade med **473 tester, 0 fel och 0 errors**. Den tidigare
fullständiga integrationssviten passerade med **215 tester, 0 fel och 0 errors**
och dokumentkontrollen passerade.

## Bankavstamning och matchning med minor units 2026-09-21

Bankavstamningen validerar nu varje bankrad innan summering. Oren eller for stora
belopp stoppas med HTTP 422 i stallet for att ett gammalt heltalsfalt anvands i
felrader, differenser eller avstamningsrapporten. Det hindrar ocksa tva felaktiga
oresbelopp fran att ta ut varandra.

Manuell matchning mot verifikationer anvander samma minor-unit-jamforelse och
stoppar fore kandidatlista eller auditpost om radens belopp inte kan uttryckas
exakt i hela kronor.

Bankimportens skapade auditposter och fakturor fran aterkommande avtal anvander
också den validerade minor-unit-skuggan innan hela kronor skrivs till auditsparet.
Det stänger ytterligare två vägar där ett legacyfält annars kunde bli det som
visades i revisionshistoriken.

Backendtesterna passerade med **473 tester, 0 fel och 0 errors**. Den fullstandiga
integrationssviten passerade med **215 tester, 0 fel och 0 errors**.

## Controllerkontroller för kreditfakturor och leverantörsexport 2026-09-21

Kundcontrollern validerar nu kundfakturans minor-unit-total innan auditspår,
statusändring och kreditfaktura skapas. Kreditfakturans netto, moms och total
hämtas från minor-unit-skuggorna och ett öresvärde stoppas med HTTP 422 före
sparning och bokföring.

Leverantörskontrollern använder samma validering för auditbelopp, betalda
belopp, makulering och CSV-export. Ett öresvärde stoppas före export eller
statusändring, så gamla heltalsfält kan inte dölja ett avvikande belopp.

Backendtesterna passerade med **466 tester, 0 fel och 0 errors**. Integrationstesterna
passerade med **215 tester, 0 fel och 0 errors** och dokumentkontrollen passerade.

## Kontantmetodens moms vid betalning och återbetalning med minor units 2026-09-21

Kontantmetodens moms vid kunddelbetalning, kundåterbetalning och
leverantörsdelbetalning läser nu fakturans total, moms och tidigare betalt från
minor-unit-skuggorna innan beloppet fördelas. Ett öresvärde stoppas med HTTP
422 före någon betalnings- eller momsverifikation sparas, så ett gammalt
kronfält kan inte dölja ett annat faktiskt belopp.

Backendtesterna passerade med **462 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Faktura- och leverantörsverifikat med minor units 2026-09-21

Skapandet av kundfakturans, leverantörsfakturans och kreditfakturans
journalrader validerar nu total, netto och moms från minor-unit-skuggorna före
verifikationsnummer reserveras. Ett öresvärde stoppas med HTTP 422 utan att
någon journalrad sparas.

Backendtesterna passerade med **464 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Kontoteckenkontroll med minor units 2026-09-21

Kontoteckenkontrollen räknar nu varje regel via journalradernas minor-unit-
skuggor. Ett öresbelopp på exempelvis bank-, moms- eller skuldkonto stoppas
med HTTP 422 innan kontot kan klassificeras som godkänt eller felaktigt.

Backendtesterna passerade med **457 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Verifikationskontroll med minor units 2026-09-21

Verifikationskontrollens journalrader och verifikatbalanser räknar nu via
journalradernas minor-unit-skuggor. Varje rad valideras före gruppsummering,
så öresposter inte kan ta ut varandra och döljas i ett helt kronbelopp. Ett
öresbelopp stoppas med HTTP 422 innan kontrollresultatet skapas.

Backendtesterna passerade med **458 tester, 0 fel och 0 errors**.

## Korrigeringar och auditbelopp med minor units 2026-09-21

Korrigeringsverifikat och leverantörsbetalningars idempotenskontroll läser nu
journalradernas minor-unit-skuggor. Ett verifikat med ören stoppas med HTTP
422 innan ett korrigeringsnummer reserveras eller en avrundad journalrad
skapas. Auditbelopp från fleradiga verifikat valideras på samma sätt innan
ändringsspåret skrivs.

Backendtesterna passerade med **459 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Resultat- och balansrapporter med minor units 2026-09-21

Resultatrapportens intäkter och kostnader samt balansrapportens tillgångar,
skulder och eget kapital räknar nu via journalradernas minor-unit-skuggor.
Varje relevant journalrad valideras innan summering, så öresposter inte kan
ta ut varandra och döljas i ett helt kronbelopp. Ett öresbelopp stoppas med
HTTP 422 innan rapporten visas.

Backendtesterna passerade med **456 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Momsavstämning med minor units 2026-09-21

Momsavstämningen räknar nu försäljning, inköp, utgående och ingående moms
från journalradernas minor-unit-skuggor. Ett öresbelopp stoppas med HTTP 422
innan avstämningen kan användas som underlag för momsbetalning.

Backendtesterna passerade med **454 tester, 0 fel och 0 errors**.

## Journal-CSV med minor units 2026-09-21

Journalexporten läser nu verifikatens minor-unit-skuggor och exporterar både
`Debet`/`Kredit` för kompatibilitet och `DebetMinor`/`KreditMinor` som exakt
kontrollvärde. Ett öresbelopp stoppas med HTTP 422 innan CSV-filen skapas i
stället för att den gamla kronrepresentationen får visa ett annat belopp.

Backendtesterna passerade med **454 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Momsarkivets CSV med radvis minor-unit-kontroll 2026-09-21

Momsarkivets export validerar nu varje deklarationsrad från minor-unit-värdet
innan de kompatibla kronfälten skrivs. Ett öresbelopp stoppas därför även när
flera rader tillsammans skulle ge en hel krona; exporten kan inte längre dölja
en avvikelse genom att bara kontrollera totalsumman.

Backendtesterna passerade med **454 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Stripe Checkout med minor units 2026-09-21

Stripe Checkout använder nu fakturans minor-unit-saldo direkt när priset skickas
till Stripe. Ett saldo med ören stoppas med HTTP 422 innan någon Stripe-session
skapas, i stället för att ett heltalsbelopp skulle kunna skickas eller avrundas
tyst. Utkast, krediterade fakturor och fakturor utan saldo fortsätter att stoppas
med sina ordinarie affärsregler.

Backendtesterna passerade med **451 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Revisionsspar och SIE-export med minor units 2026-09-21

Revisionsspårets kedjehash, journalintegritet och SIE-export läser nu
verifikatens minor-unit-skuggor. Kedjan binder de exakta minor-värdena och
SIE-exporten jämför verifikat i minor units innan den konverterar till den
äldre tvådecimaliga SIE-representationen. Ett öresbelopp stoppas med HTTP
422 i stället för att exporteras som ett felaktigt heltalsbelopp.

Backendtesterna passerade med **450 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Huvudbok och saldobalans med minor units 2026-09-21

Huvudbok och saldobalans räknar nu ingående saldo, periodrörelser, löpande
saldo och utgående saldo via huvudbokens minor-unit-skuggor. Ett öresbelopp
stoppas med HTTP 422 innan det kan visas som ett felaktigt heltalsbelopp.

Backendtesterna passerade med **449 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Periodstangning och reskontrakontroll med minor units 2026-09-21

Periodstangningens senbokforingskontroll och kund/leverantorsreskontrans
huvudbokskontroll laser nu huvudbokens debet och kredit via minor-unit-skuggor.
Om ett orebelopp inte kan representeras i de aldre kronbaserade rapporterna
stoppas kontrollen med HTTP 422 i stallet for att perioden eller avstamningen
felaktigt markeras som godkand.

Backendtesterna passerade med **448 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Momsrapportens minor-unit-kontroll 2026-09-21

Momsrapporten summerar nu huvudbokens debet/kredit via minor-unit-skuggorna.
Momsarkivet exporterar dessutom `UtgaendeMomsMinor`, `IngaendeMomsMinor` och
`MomsAttBetalaMinor`. Om ett öresbelopp inte kan visas korrekt i den äldre
kronrapporten stoppas rapporten eller exporten med HTTP 422.

Backendtesterna passerade med **446 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Kund- och leverantörsreskontrans minor-unit-kontroll 2026-09-21

Kund- och leverantörsreskontran räknar nu betalningshistorik, kvarvarande
saldo, netto, moms och total internt i minor units. De äldre rapportfälten får
bara användas när beloppen kan representeras exakt i hela kronor. Öresaldon
stoppas med HTTP 422 i stället för att avrundas tyst.

Backendtesterna passerade med **445 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Kundreskontrans minor-unit-kontroll 2026-09-21

Kundreskontrans saldokontroll och betalningshistorik räknar nu internt i
minor units. Betalningar summeras med overflow-skydd och rapportens äldre
heltalsfält får bara användas när saldot kan representeras exakt i hela
kronor. Ett saldo med ören stoppas med HTTP 422 i stället för att avrundas
tyst. Den vanliga kundreskontran är fortsatt verifierad mot PostgreSQL.

Backendtesterna passerade med **444 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Fakturadokument och exportens minor-unit-kontroll 2026-09-21

Fakturans PDF läser nu minor-unit-skuggvärden med fallback för äldre poster och
visar ören med två decimaler när de finns. CSV-exporten behåller de befintliga
heltalskolumnerna och kompletterar dem med `NettoMinor`, `MomsMinor` och
`TotaltMinor`. Exportens audit-summa stoppas om den innehåller ören som den
nuvarande hela-kronorsrapporten inte kan representera.

Backendtesterna passerade med **443 tester, 0 fel och 0 errors** och
integrationstesterna med **215 tester, 0 fel och 0 errors**.

## Huvudbokens debet/kredit och integritet 2026-09-20

Journalrader har nu versionssatta `bigint`-skuggfält för debet och kredit.
Nya rader fyller skuggvärdena, äldre rader backfillas idempotent och
verifikationens integritetshash binder minor-unit-värdena. Legacy-fälten i hela
kronor är fortsatt auktoritativa under migreringen; detta är ett precision- och
spårbarhetssteg, inte ett godkännande för skarp bokföring.

Backendtesterna passerade med **442 tester, 0 fel och 0 errors** och
integrationstesterna med **214 tester, 0 fel och 0 errors**.

## Bankavstämningens minor-unit-kontroll 2026-09-20

Bankavstämningen jämför nu konto 1930 mot bankrader via exakta minor-unit-skuggor
och stoppar rapporten om ören inte kan representeras av dagens heltalsrapport.
Det skyddar mot att en bank-/huvudboksskillnad avrundas bort under den stegvisa
beloppsmigreringen. Backendtesten är fortsatt `442` och integrationstesten `214`,
båda utan fel.

## Kostnader, kvitton och bokforingsspar 2026-09-20

Kostnader har nu separata `bigint`-skuggfält för netto, moms och total. Nya
kostnader synkroniserar fälten vid sparning och äldre poster synkroniseras vid
inläsning under den stegvisa migreringen. Bokföringsposterna läser minor-unit-
värdena och stoppar öresbelopp som den nuvarande hela-kronorsmodellen inte kan
representera utan förlust. Kvittoflödet behåller samma atomiska databas- och
auditbeteende.

Backendtesterna passerade med **442 tester, 0 fel och 0 errors** och
integrationstesterna med **214 tester, 0 fel och 0 errors**.

## Leverantorsreskontrans betalningssaldo 2026-09-20

Leverantorsfakturans kvar-att-betala och betalningsflode raknar nu internt i
minor units och stoppar betalningar som skulle overskrida fakturans total.
Legacy-kolumnerna ar fortsatt auktoritativa under den stegvisa migreringen;
skuggfalten synkroniseras vid lasning och sparning sa aldre importer inte kan
lamna ett stale minor-unit-saldo.

Backendtesterna passerade med **442 tester, 0 fel och 0 errors** och
integrationstesterna med **214 tester, 0 fel och 0 errors**.

## Kundfakturans saldo, betalning och aterbetalning 2026-09-20

Kundfakturans kvar-att-betala, betalnings- och aterbetalningsflode raknar nu
internt i minor units och stoppar overbetalning och overaterbetalning. Legacy-
kolumnen ar fortfarande auktoritativ under den stegvisa migreringen; fakturans
minor-unit-skuggfalt synkroniseras darfor vid lasning sa aldre poster och
avstamningskorrigeringar inte kan anvanda stale skuggdata.

Backendtesterna passerade med **442 tester, 0 fel och 0 errors** och
integrationstesterna med **214 tester, 0 fel och 0 errors**. Den isolerade
backup/restore-drillen passerade **16/16** syntetiska kontroller.

## Stripe-utbetalningar och momsunderlag 2026-09-20

Stripe-utbetalningar och sparade momsunderlag har nu versionssatta `bigint`-
skuggfält i minor units för brutto, avgift, netto och momsens delbelopp. Nya
poster skriver skuggvärden samtidigt som befintliga hela-kronor och äldre poster
backfillas idempotent. De gamla heltalsfälten är fortsatt auktoritativa tills
hela öre-migreringen och historiska avstämningen är verifierade.
Vid JPA-lasning stoppas en avvikande skuggkopia; vid sparning synkroniseras
skuggkopian fran legacyvardet. Detta ar ett dataintegritetsskydd, inte ett
godkannande av hela ore-migreringen.

Backend- och integrationstesterna ligger kvar på **442 respektive 214 tester**.
Den isolerade backup/restore-drillen passerade **16/16** syntetiska kontroller.

## Kortköp och bankavstämning 2026-09-20

Kortköp och bankrader har nu versionssatta `bigint`-skuggfält i minor units.
Nya poster skriver skuggvärden samtidigt som befintliga hela-kronor, och äldre
poster backfillas idempotent. Bankradens tecken bevaras även i skuggvärdet.
De gamla heltalsfälten är fortfarande auktoritativa tills hela öre-migreringen
och historiska avstämningen är verifierade. Backend- och integrationstesterna
ligger kvar på **442 respektive 214 tester**.

## Kostnader och skuggmigrering 2026-09-20

Kostnader har nu separata `bigint`-skuggfält för netto, moms och total. Nya
kostnader skriver fälten samtidigt som de befintliga hela-kronorna, och äldre
poster backfillas idempotent. Kvittoflödet behåller samma atomiska databas- och
auditbeteende. `npm run test:backend` passerade med **442 tester, 0 fel och 0
errors** och `npm run test:integration` passerade med **214 integrationstester,
0 fel och 0 errors**.

Skuggfältet är en förberedelse; gamla heltalsfält är fortfarande auktoritativa
tills hela öre-migreringen, historiska avstämningen och rapportmigreringen är
verifierade.

## Manuella verifikat kräver bokföringsdatum 2026-09-20

Enkel verifikation, flerradig verifikation, ingående balans, rättelse och
Stripe-utbetalning avvisas om bokföringsdatum saknas. Inget verifikatnummer
eller någon journalrad skapas innan datumet är uttryckligt och periodlåset kan
kontrolleras. `AccountingServiceTest` provar dessa stopp och hela
`npm run test:backend` passerade med **442 tester, 0 fel och 0 errors**.

Frontendens beloppsformular rundar inte langre decimalinput tyst. `parseWholeSekInput`
stoppar decimaler och varden over nuvarande heltalsgrans innan de skickas till
backend; det ar ett skydd for dagens modell och inte ett pastande om fardigt orestod.

Skuggmigreringen `core-invoice-shadow-v1` ar tillagd som en additiv och
aterstallningsbar databasforberedelse. Den kopierar karnflodets hela kronor till
separata `bigint`-falt i oren, men applikationen anvander fortfarande de gamla
falten tills hela migreringen ar verifierad.

## Verifikat och audit är atomiska 2026-09-20

Manuella verifikat, ingående balans, rättelser, Stripe-bokningar och
webbplatsförsäljning kör nu journaländring och auditregistrering i samma
transaktion i `AccountingController`. Om auditspåret misslyckas rullas även
journalraderna tillbaka. Den isolerade PostgreSQL-körningen
`npm run test:integration` passerade med **214 integrationstester, 0 fel och 0
errors**.

## Kvittofil och bokföringsspår är atomiska 2026-09-20

Kvitton valideras mot både angiven MIME-typ och filsignatur innan de arkiveras.
Uppladdningen kör nu databasändring och `receipt_uploaded`-audit i samma
transaktion. Om sparandet eller auditposten misslyckas tas den nya filen bort,
så ett kvitto inte blir en lös fil utan databaskoppling. Arkiverade kvitton kan
inte ersättas; deras SHA-256 kan repareras och kontrolleras separat.
Regressionstestet för auditfel ingår i `ExpenseControllerTest` och hela
`npm run test:backend` passerade med **440 tester, 0 fel och 0 errors**.

## Leverantörsfakturans status följer rätt periodlås 2026-09-20

Statusändringar på leverantörsfakturor kontrollerar fakturadatum mot periodlåset
när statusändringen gäller själva fakturan. En betalning kontrollerar i stället
det uttryckliga betalningsdatumet, så en gammal leverantörsfaktura kan betalas i
en senare öppen period. Detta är ett lokalt verifierat kontrollsteg; det
ersätter inte den kvarvarande öre-migreringen eller produktionsverifiering av
hela reskontra- och betalningsflödet.

## Bokföringsdatum måste anges uttryckligen 2026-09-20

Kundbetalningar, återbetalningar, kostnader och leverantörshändelser accepterar inte längre ett saknat datum genom att tyst använda dagens datum. Ett uttryckligt datum krävs innan bokföring eller reskontrastatus sparas, så poster inte flyttas till fel period utan synlig varning. Leverantörsfakturans fakturadatum, betalningsdatum och makuleringsdatum har egna tester.

Samma stopp finns nu även i `AccountingService`, så en framtida endpoint eller
intern anropare kan inte kringgå regeln genom att skicka `null`. Betalning,
återbetalning, Stripe-försäljning, leverantörsbetalning, makulering och
momsbetalning kräver explicit datum innan journalrader skapas.
`AccountingServiceTest` verifierar att saknat datum stoppas före bokföring.

## Fakturamejlens skickad-status 2026-09-20

Fakturans `INVOICE_EMAIL`-historik och `email_sent`-audit sparas nu först efter
att SMTP-anropet lyckats. Ett misslyckat utskick lämnar inte fakturan som
skickad i samma transaktion. `OrderControllerTest` provar detta och hela
`npm run test:backend` passerade med **436 tester, 0 fel och 0 errors**.
Detta minskar risken för falsk skickad-status men ersätter inte en permanent
email-outbox eller extern SMTP-verifiering före skarp drift.

## Bankimport bevarar saknat datum 2026-09-20

Bankimporten fyller inte längre i dagens datum när en CSV-rad saknar datum.
Raden visas i stallet som en kritisk avstamningsavvikelse, sa historik inte
flyttas till fel period. `BankReconciliationServiceTest` provar detta.

## JSON-beloppsprecision och CI-action 2026-09-18

`npm run test:backend` passerade med **419 tester, 0 fel och 0 errors**.
Detta inkluderar `JsonNumberConfigTest`: heltals-SEK accepteras och ett JSON-
decimalbelopp som `10.50` avvisas i stallet for att kapas till `10`. Det
bekraftar den befintliga helkronemodellen; det innebar inte att ore-stod har
infors.

GitHub Dependabot PR #6 misslyckades eftersom dess uppgradering av
`actions/setup-java` till v6 inte matchade projektets statiska kontroll som
fortfarande kravde v5. Arbetsflodet och kontrollen ar nu bada v6; `check:ci`
passerade 39/39 och den lokala `check:release` passerade. Fixen ar annu inte
pushad, sa PR:ens externa status ar inte uppdaterad. Ingen production- eller
anvandardatabas anvandes.

## Isolerad backup- och restoretest 2026-09-18

`npm run test:backup` passerade med **16/16** syntetiska kontroller. Testet
skapade och aterstallde en PostgreSQL-dump i en isolerad Docker-container,
kopierade kvittofiler och verifierade SHA-256, journalbalans, saknade/skadade
underlag, ogiltig dump, aterstallningsvagar och att befintliga backupmappar inte
skrivs over. Testdata var syntetiska; containern togs bort efter testet.

Detta godkanner inte anvandarens riktiga backup eller produktionsaterstallning.
Krypterad separat kopia, fullstandig data- och filinventering samt appflod efter
restore maste fortfarande provas i en separat aterstallningsmiljo.

## Isolerad backup- och restoretest upprepad 2026-09-23

`npm run test:backup` passerade igen med **16/16** syntetiska kontroller. Den
andra korningen bekraftar att backupverktyget fortfarande aterstaller dump,
underlag och den versionssatta pengamodellen atomart, och att verifieringen
stoppar saknade eller andrade kvitton, obalanserade verifikat, ogiltig dump och
otillaten deployvolym. Den tillfalliga Docker-containern togs bort efter provet.

Detta ar ett regressionsbevis for restore-koden, inte ett godkannande av riktig
kunddata. En faktisk krypterad backup, separat lagringsplats och appens klickflode
mot aterstalld verksamhetsdata maste fortfarande provas och signeras manuellt.

## Bankavstamning och manuell journalmatchning 2026-09-23

Bankkontrollerna passerade **25/25** riktade testfall: bank- och journalbelopp
summeras i minor units, dubblett- och identitetsfel blir kritiska avvikelser,
periodlas respekteras och en manuellt vald journalrad med fel datum eller belopp
kan inte kopplas till bankraden. Det nya regressionsprovet skyddar sarskilt
matchningsvagen fran att kringga datum- och beloppskontrollen.

Detta bevisar kontrollkedjan med testdata. Verkliga kontoutdrag, historiska
ingangsbalanser och fullstandig aldre bankhistorik maste fortfarande stammas av
manuellt fore skarp drift.

Den lokala `npm run doctor` passerade 8/8 mot aktuell utvecklingsmiljo, och
`npm run smoke:runtime` passerade i en separat headless Chrome-profil. Smoken
verifierade oversiktens rendering, inloggningskontroller och aterhamtning fran
en sparad intern vy nar anvandaren ar utloggad. Detta ar inte ett inloggat
verksamhetsflod eller produktionsbevis.

## Schemalagd avtalsfakturering 2026-09-18

Backend avvisar nu avtalsfakturor innan nasta fakturadatum och nar datum saknas;
frontend visar avtalet som schemalagt och inaktiverar fakturaknappen innan
forfallodagen. `RecurringContractControllerTest` passerade med 5 tester och 0
fel, inklusive att for tidig fakturering inte gor kund-, tjanste-, bokforings-
eller fakturaskrivningar. Testet kor separat fran full release gate.

## Fakturakontroller och senaste lokala verifiering 2026-09-18

Efter fakturakontrollerna passerade `npm run test:integration` med 413
enhetstester och 213 PostgreSQL-integrationstestfall, totalt **626**, utan
failures, errors eller hoppade tester. Detta inkluderar koparadresskrav for
fakturor over 4 000 SEK inklusive moms, gransfallet pa exakt 4 000 SEK,
originalets synliga fakturanummer pa kreditfaktura och kreditens snapshot efter
PostgreSQL-omlasning. Databasen var en isolerad och borttagen testdatabas.

`npm run check:release` passerade separat med frontend-produktionsbuild,
runtime-smoke, 49 menyer/vyreferenser och releasekontroller. Denna gate kor inte
backendtesterna eller bygger Docker-images; backendbeviset ovan ar separat.
Ingen GitHub Actions-korning, publicering, extern e-post/bank/Stripe-koppling,
produktion eller anvandarens vanliga databas anvandes.

Backendens produktionsstart blockeras nu ocksa direkt av konfigurationsvakten sa
lange pengamodellen lagrar hela SEK. Satta inte en konfigurationsflagga for att
runda stoppet; det ska tas bort forst nar versionssatt ore-migrering, historisk
avstamning och fulla tester faktiskt finns. Det riktade testet for denna
produktionssparr passerade med 7 tester och 0 fel den 2026-09-18, inklusive
Spring-kontextkontroll for produktionssparr och lokal miljo; testets
PostgreSQL-container avvecklades efter korningen.

Fakturavalideringen kravver tjanstebeskrivning, saljarprofil och
momsregistreringsnummer vid moms. Over 4 000 SEK kravs koparens namn och
fullstandiga adress. PDF visar enhetspris exklusive moms och beskattningsunderlag;
kredit-PDF hanvisar till originalets fakturanummer. Detta ar grundkontroller,
inte en garanti om fullstandig regelefterlevnad for alla fakturatyper.
Skatteverkets faktureringsregler finns [har](https://www.skatteverket.se/foretagochorganisationer/moms/saljavarorochtjanster/fakturering.4.58d555751259e4d66168000403.html).

## Kontoskydd och tidigare lokal verifiering 2026-09-18

`npm run test:backend` passerade med 413 enhetstester och noll fel. Den isolerade
Den tidigare korningen fore de senaste fakturaintegrationstesterna passerade med
413 enhetstester och 209 PostgreSQL-integrationstestfall, totalt **622**, utan
failures, errors eller hoppade tester.
Det inkluderar att en registrering skapar agarkontot och att nasta registrering
nekas. Testcontainrar och testdatabas avvecklades.

`npm run check:release` passerade med frontend-produktionsbuild, API-/vy-
kontroller, runtime-smoke och releasebevis. `npm run check:env-go-live` passerade
41/41. `npm run check:prod` visar 45/46 och en forvantad varning eftersom
`.env.production.example` avsiktligt innehaller en platshallare for
`APP_AUTH_REGISTRATION_BOOTSTRAP_KEY`; anvand en separat slumpad nyckel i riktig
miljo. `check:use-today` rapporterar en varning for den avsiktligt andrade
worktree:n.

Produktionens forsta agarkonto kravs nu skyddas av startnyckel; oppen registrering
stangs nar kontot skapats. Detta ar en enforetags-/agarmodell, inte
flerforetagsisolering. Riktiga bokforingsposter ar fortfarande blockerade tills
ore-stod och migrering, verklig backup/restore, extern CI/deploy och
redovisningskontroller ar verifierade. Inget har pushats eller provats mot
anvandarens riktiga databas.

Efter tillagg av radlas for samtidiga rattelseverifikationer kordes hela
enhetssviten igen: 413 passerade. Det nya isolerade PostgreSQL-testet
`concurrentCorrectionRequestsCreateOnlyOneCorrectionVoucher` passerade ocksa.
Detta ar ett riktat integrationstest efter lasandringen, inte en ny full
integrationstestsvit.

## Original-PDF-arkiv och integritetskontroll 2026-09-16

Slutlig `npm run test:integration` passerade: 365 enhetstester och 207
PostgreSQL-integrationstestfall, totalt **572**, utan failures, errors eller
hoppade tester. Testcontainrar och isolerad databas avvecklades med exit code 0.

Nya utstallda fakturor och krediter arkiverar PDF-bytes i invoice_originals
med SHA-256. Nedladdning och e-postbilaga laser samma arkiverade bytes;
betalning och registerandringar kan inte skriva om originalet. Skadad fil eller
kontrollsumma ger HTTP 409 i stallet for tyst nygenerering. Foreign key och
unik faktura-ID skyddar mot orphan/originalersattning. Aldre fakturor utan
arkiv markeras fortsatt rekonstruerade och backfylls inte.

Den forsta arkivmodellen gav Hibernate null identifier i 94 integrationstest-
fall. `@OneToOne/@MapsId` ersattes med explicit faktura-ID och databasens
foreign key; hela sviten kordes om och ar gron. `node scripts/schema-migration-check.mjs`
passerade 5/5 med 292/292 startup-satser. `node scripts/ci-pipeline-check.mjs`
passerade 39/39. Lokal `npm run check:release` passerade med produktionsbuild
och runtime-smoke.

Ingen riktig e-post, anvandarens databas eller migration mot produktion
anvandes. GitHub Actions ar inte verifierad for dessa lokala andringar.
Originalarkivet loser inte att SMTP-acceptans och databascommit ar separata;
en bestaende outbox och avstamning behovs fore skarp drift. Fullt ore-stod,
verifierad historik/ingangsbalans, kontantmetodens bokslut och externa drift-
kontroller kvarstar ocksa. Se [invoice-document-snapshots.md](invoice-document-snapshots.md).

## Fakturadokument, avtalsrollback och e-postordning 2026-09-09

Slutlig `npm run test:integration` passerade: 339 enhetstester och 199
PostgreSQL-integrationstestfall, totalt **538**, utan failures, errors eller
hoppade tester. Detta steg tillfor 22 enhetstestfall och 15 integrationstestfall.
Testcontainrar och deras isolerade databas avvecklades med exit code 0.

Verifierat: registerandringar skriver inte om nya fakturors PDF-uppgifter;
snapshoten aterlasas fran PostgreSQL och arvs av krediten; aldre NULL-snapshot
backfylls inte vid PDF-lasning; stor fakturaexport avvisas utan auditframgang.
Avtalsfaktura, nasta datum och audit rullas tillbaka tillsammans vid loggfel.
Oversized avtal sparas inte. Mejlets transport mockas: kontroller/bokforing
kommer fore SMTP, fel rullar tillbaka och aterutskick av utstalld faktura,
betald faktura eller kredit skapar inga nya journalrader.

Syntetiska partial-payment.pdf och credit.pdf i backend/target/pdf-proof
renderades och granskades visuellt efter slutversionens enhetstester.
Delbetalning 50 av 125 visar 75 kvar; kredit visar -125 utan betalningsbegaran.
CI:s befintliga backendartefakt inkluderar nu dessa PDF-prov.

Slutlig lokal `npm run check:release` passerade inklusive produktionsbuild och
runtime-smoke. Backendtesterna ovan kordes separat; produktionsbilder byggdes
inte. `node scripts/ci-pipeline-check.mjs` passerade 39/39 och
`node scripts/schema-migration-check.mjs` passerade 5/5 med 290/290 SQL-satser.
`git diff --check` passerade. Ingen riktig SMTP, migrering av anvandarens databas,
betalning eller GitHub-push har gjorts i detta steg. GitHub CI ar inte verifierad
for dessa lokala andringar.

Se [invoice-document-snapshots.md](invoice-document-snapshots.md).
**Inte klar som enda bokforingssystem:** fullt ore-stod, verifierad historik och
ingangsbalans, kontantmetodens bokslut, originaldokumentarkiv, SMTP/commit-
atervinning och aterstaende driftkontroller kvarstar. En dokument-snapshot ar
inte ett oforanderligt arkiv av de skickade PDF-bytesen.

## Journalradskoppling for bankavstamning 2026-09-09

Slutlig `npm run test:integration`: 317 enhetstester och 184 PostgreSQL-testfall,
totalt **501**, utan failures, errors eller hoppade tester. Detta steg tillfor
9 enhetstester och 16 integrationstestfall. Nollnetto med saknade kopplingar,
felaktiga kopplingar, unikhet/foreign key, samtidig matchning, audit-rollback,
periodlasning, autentisering, HTTP-svar och additiv/upprepad migrering provas.
Forsta korningen hade ett fel i den nya testfragans kolumnnamn for audit;
event_action rattades och hela sviten kordes om. Testcontainrarna avvecklades.

`npm run test:reports`: 113 godkanda tester. Bankens riktiga React-vy provades
med mockade API-svar i separat webblasarkontext: inget forvalt matchningsval,
409 behaller raden okopplad, lyckad bekraftelse sparar journal-ID utan ny
betalning, och CSV-exporten innehaller bankradens och journalradens ID.
Mobil 390x844 och desktop 1280x900 provas; mobilens historikrad rattades efter
visuell granskning och testet kontrollerar att falt och knappar ryms i raden.

`npm run check:release` passerade, inklusive 26 API-kontrakt och runtime-smoke.
Efter tillagget av CSV-kolumner passerade produktionsbuild och webblasarprov igen.
`git diff --check` passerade. Forsoket med `check:release:full` stoppades vid
Docker-atkomst i sandboxen; backend korningen ovan gjordes sedan separat med
godkand Docker-atkomst. Produktionsbilder har inte byggts i detta steg.

Se [bank-journal-links.md](bank-journal-links.md) for API, migration och handhavande.
Ingen riktig bankrad har kopplats och ingen migrering har korts mot anvandarens
databas. Gamla kopplingar maste granskas uttryckligen. Andringarna ar lokala,
inte pushade; GitHub CI ar inte verifierad for denna version. Befintlig CI kor
de nya Maven-testerna och API-kontrakten; webblasarprovet har korts lokalt.

**Inte redo som enda bokforingssystem:** oren, verifierad historik/ingangsbalans,
klumpsummor, kontantmetodens bokslut och aterstaende driftkontroller kvarstar.

## Atomisk bankimport 2026-09-09

Slutversionens `npm run test:integration` passerade: 308 enhetstester och 168
PostgreSQL-integrationstestfall, totalt 476, utan failures, errors eller hoppade
tester. De 22 nya integrationstestfallen provar atomisk bankbokning, samtidiga
dubbletter, rollback, autentisering, periodlasning, datum och fullstandiga belopp.
Forsta korningen hade ett fel i testklientens hantering av HTTP 401 i streaming
mode; autentiseringsprovet bytte till Java HttpClient och hela sviten kordes om.
Testcontainrarna avvecklades efter korningen med exit code 0.

`npm run test:reports` passerade 113 tester, inklusive 34 nya banktester.
`scripts/bank-import-ui-test.mjs` provade den faktiska React-vyn med testdata
och blockerade alla verkliga backendanrop. Serverfel och 409 behaller bankraden;
lyckad sparning tar bort den och overbetalning stoppas innan anrop. Desktop
1280x900 och mobil 390x844 granskades. Mobilens rutnat och bankknappar rattades
efter att breddprovet hittade overflow. Ingen generell redesign ingar.

Slutlig lokal `npm run check:release` passerade inklusive produktionsbuild,
24 API-kontrakt och runtime-smoke. Datasakerhetskontrollen kontrollerar nu den
flyttade skipped-raderingen i tjansten och dess periodskydd. Dockerproduktionsbilder
byggdes inte; backendtesterna ovan kordes separat fran frontendens release-gate.

Nya bankanrop, bokforing, historik och audit ar en transaktion. Separat POST
av pastadd booked-historik avvisas. CSV-identiteter ar stabila for oforandrade
rader, men ar inte bankens transaktions-ID och migrerar inte gamla rader.
Se [bank-import-atomicity.md](bank-import-atomicity.md) for omfattning och risker.

**Inte redo som enda bokforingssystem:** oren, historiska importer/ingangsbalanser,
fullstandig transaktionsvis bankavstamning och ovriga go-live-kontroller kvarstar.
Ingen riktig betalning, e-post, kostnad eller bokforingspost skapades i detta steg.
Andringarna ar lokala, inte pushade; GitHub CI ar inte verifierad for denna version.
Backend maste startas om och bankfilen lasas in pa nytt for att prova de nya anropen.

## Verklig lokal backup och isolerad aterlasning 2026-09-09

Efter anvandarens bekraftelse att CloudshopApplication pausats kontrollerades
att port 3000 inte lyssnade. PostgreSQL lamnades igang. `npm run backup:local`
skapade `backups/local-20260909T192913Z/verified-manifest.json` efter godkand
aterlasning i en separat tillfallig PostgreSQL-container utan natverk eller
vardmonteringar. Originaldatabasen och originalfilerna andrades inte.

Verifierat: 74 journalrader, samma radantal i samtliga public-tabeller fore och
efter backup samt i aterlast kopia, och nio kopierade filer med matchande SHA-256.
Verifikationsbalanskontrollen passerade. Manifestet skapades 19:30:01 UTC.
**Nio filer saknar kostnadskoppling och en kostnad saknar kvittoreferens.** Antalet
databaskopplade verifierade kvitton ar darfor noll. Inga kopplingar gissades eller
skapades automatiskt. Filernas affarsmassiga tillhorighet maste granskas separat.

Slutversionens `npm run test:backup` passerade 16/16 syntetiska tester, inklusive
samlad backup, radantalsjamforelse, okopplade filer, saknade referenser och
avvisning av ateranvand backupmapp. `npm run check:backup` passerade 19/19.
Ingen ny backend- eller frontendtestkorning ingar i detta steg.

Backupen ar lokal, Git-ignorerad och inte krypterad av verktyget. En skyddad
kopia pa annan lagringsplats och appens floden mot aterlast data ar fortfarande
inte verifierade. Radantal, filhashar och verifikationsbalans ar inte ett
godkannande av originalbokforingen. Fullt ore-stod och ovriga go-live-blockerare
kvarstar. Kodandringarna ar inte pushade eller verifierade i GitHub CI.

## Samtidig periodlasning och bokforing 2026-09-09

`npm run test:integration` passerade pa slutversionen: 308 enhetstester och
146 PostgreSQL-integrationstestfall, totalt 454, utan failures, errors eller
hoppade tester. Den forsta korningen hittade ett felaktigt SQL-kolumnnamn i ett
nytt test; testfragan rattades till event_action och hela sviten kordes om.
Testcontainrarna avvecklades och kommandot avslutades med exit code 0.

Sju nya integrationstestfall verifierar periodlasning fore bokforing, bokforing
fore periodlasning, gammal JPA-cache, tillaten bokforing efter lasdatum,
samtidiga lasningar, rollback vid auditfel, vantande installningsandring samt
krav pa en yttre transaktion. Tva nya enhetstester kontrollerar refresh med
PESSIMISTIC_WRITE och avvisning nar installningsraden saknas.

`npm run test:reports` passerade 79 frontendtester. Backend wiring och
period-close-kontrollerna passerade. `npm run check:release` passerade inklusive
frontendbuild och runtime-smoke. Produktions-Dockerbilder byggdes inte.

Skyddet ligger bakom befintliga floden och kraver ingen ny meny. En gemensam
databaslasning varar genom kontroll, journalbokforing eller periodlasning till
commit/rollback. Se [period-write-serialization.md](period-write-serialization.md).

Andringarna ar lokala och inte pushade; GitHub CI ar inte verifierad for denna
version. Den riktiga CloudshopApplication startades inte om. Ingen verklig
bokforing, betalning, e-post eller produktionsdatabas andrades.

**Fortfarande inte redo som enda bokforingssystem:** fullt ore-stod,
verifierade importer/ingangsbalanser, kontantmetodens bokslutsflode, verklig
backupaterlasning och ovriga go-live-kontroller kvarstar.

## Exakta importbelopp 2026-09-09

Bank-CSV och SIE-analys har separata testbara moduler. Belopp parsas till BigInt
i oren, utan flyttalsavrundning eller borttagning av godtyckliga tecken. En bankfil
med unsupported oren stoppas helt; SIE visar beloppet men stoppar bokforing.
Gamla cachade SIE-analyser, saknade datum, tvetydiga bankkolumner och forvrangda
belopp godkanns inte av importkontrollen. En ore i differens ar inte balans.

`npm run test:reports` passerade 79 tester (41 nya importtester och 38 tidigare
rapporttester). Detta kommando kors redan i frontendjobbet i CI, men den nya
versionen ar inte pushad och har inte verifierats pa GitHub. Lokal release-gate
passerade inklusive build och runtime-smoke. Backendkoden andrades inte i detta
steg; tidigare 445 backendtestresultat ar inte en ny backendkorning.

Inga verkliga importfiler eller bokforingsposter andrades. Detta steg hindrar
tyst forlust av oren vid dessa importer, men migrerar inte databasen eller andra
beloppsfloden. Fullt ore-stod och ovriga go-live-blockerare aterstar. AliBooks ar
fortfarande inte godkant som enda bokforingssystem. Se money-safety.md.

## Reskontra mot huvudbok 2026-09-09

`npm run test:integration` passerade med 306 enhetstester och 139 integrationstestfall:
445 totalt, inga failures, errors eller hoppade tester. Testcontainrarna avvecklades.
Den nya kontrollen jamfor 1510/2440 per faktura och totalt. Integrationstester
verifierar delbetalningar, kredit/aterbetalning, felkopplade betalningar med oforandrad
totalsumma, saknade kallkopplingar, autentisering och blockerad periodlasning.

`npm run test:reports` passerade 38 frontendtestfall. `npm run check:release`
passerade inklusive produktionsbuild och runtime-smoke. Separat Playwright-prov
`scripts/subledger-ui-test.mjs` passerade med isolerade mockade API-svar:
desktop 1280x800, mobil 375x812, aterforsok, HTTP-fel, ogiltiga rapportsvar,
datumbyte under pagaende hamtning och sessionsbyte. Skarmbilder granskades;
mobilens tabell rullar horisontellt utan att bredda sidan. Testets ursprungliga
Vite/React-importfel rattades i testharnessen innan slutprovet passerade.
Detta ar komponentprov, inte ett fullstandigt inloggat end-to-end-flode.

Under Avstamning finns nu Reskontra mot huvudbok. Fel och ofullstandig historik
ger inte ett godkant saldo. Kontantmetoden ar uttryckligen ej stodd av denna
automatiska jamforelse och blockerar periodlasning tills kontrollerat bokslutsflode
finns. MATCHED ar inte ett bevis pa fullstandiga importer eller skarp driftklarhet.

Andringarna ar lokala, inte pushade. GitHub Actions och produktionsbilder har inte
verifierats for denna version. Den riktiga CloudshopApplication har inte startats om;
inga riktiga bokforingsposter, betalningar, underlag eller e-postutskick andrades.

**Inte godkant som enda bokforingssystem.** Fullt ore-stod, verkliga importer och
ingangsbalanser, kontantmetodens bokslut, transaktionsvis bankmatchning, gemensam
serialisering av periodlasning/bokforing och verklig backupaterlasning aterstar.
Se [subledger-control.md](subledger-control.md) och go-live-riskregister.md.

## Historiska reskontrasaldon 2026-09-09

`npm run test:integration` passerade med 291 enhetstester och 133 integrationstestfall:
424 totalt, inga failures, errors eller hoppade tester. Detta steg lade till
30 enhetstestfall och 10 integrationstestfall jamfort med leverantorsbetalningsskyddet.
Den isolerade PostgreSQL-testmiljon avvecklades efter korningen.

Backendens kund-/leverantorsreskontra och CSV-export beraknar nu saldo fran
fakturadatum, daterade betalningar och krediter/makuleringar. Testerna provar
saldo fore, pa och efter betalningsdatum, fullbetalning, kredit, aterbetalning,
makulering, felaktig historik samt tidigare radering. HTTP/CSV-testfallen provar
verkligt sparade del- och slutbetalningar och att fel inte skriver journal/audit.
Databasfelen i felinjektionstesterna ar avsiktliga och kontrollerar rollback.

Registrerade leverantorsfakturor kan inte raderas, inte heller obetalda
kontantmetodsfakturor. Makulering kravs med datum. Frontendens raderingsknapp och
raderingsfunktion for leverantorsfakturor ar borttagna. Flerradiga betalningsreferenser
avvisas sa att nya referenser inte kan skada det befintliga historikformatet.

`npm run test:reports` passerade 19 frontendtestfall. Sista `npm run check:release`
passerade pa slutversionen, inklusive produktionsbuild och webblasarens runtime-smoke.
Statiska raderingskontroller uppdaterades till den nya striktare bevaranderegeln;
integrationsprovet verifierar att den riktiga databasraden finns kvar efter avvisad
radering. Runtime-smoke testar startsida/inloggningsskal, inte alla inloggade vyer.

Frontend pa http://localhost:5157 gav HTTP 200. Den riktiga CloudshopApplication
har inte startats om. Andringarna ar lokala och inte pushade till GitHub; inga
produktions-Dockerbilder byggdes och inga riktiga bokforingsposter, betalningar,
e-postutskick eller underlag andrades.

**Fortfarande inte godkant som enda bokforingssystem.** Fullt ore-stod,
reskontra/huvudboksavstamning, verifierade importer/ingangsbalanser och verklig
backupaterlasning aterstar. Historiken bygger pa sparade handelser, inte pa
frysta historiska kunduppgifter eller bevisad fullstandighet hos aldre importer.
Se [historical-settlement-reports.md](historical-settlement-reports.md).

## Leverantorsbetalningar och saldokontroller 2026-09-09

`npm run test:integration` passerade med 261 enhetstester och 123 integrationstestfall:
inga failures, errors eller hoppade tester. Detta steg lade till 13 enhetstestfall
och 17 integrationstestfall jamfort med rapportskyddet nedan.

Verifierat mot isolerad PostgreSQL: samtidiga leverantorsbetalningar bevarar bada
beloppen; dubbletter och overbetalningar avvisas; makulering kan inte radera en
betalning och betalning kan inte ateraktivera en makulerad faktura. Journal, saldo,
historik och audit aterstalls tillsammans vid fel. En redan bokford faktura fran
en last period kan betalas pa ett tillatet datum i en oppen period.

Bankavstamning, kund-/leverantorsreskontra och leverantorsexport summerar med
kontrollerade mellanbelopp. Overskriden rapportkapacitet ger HTTP 422 med
REPORT_AMOUNT_LIMIT, inte ett lyckat svar med fel saldo eller en felaktig export.

`npm run test:reports` passerade 19 frontendtestfall. `npm run check:release`
passerade inklusive produktionsbuild och webblasarens runtime-smoke. Produktions-
Dockerbilder byggdes inte i detta steg. Testcontainrarna avvecklades efter testen.
Andringarna ar lokala, inte pushade eller verifierade i GitHub Actions. Den riktiga
CloudshopApplication-processen har inte startats om. Inga riktiga betalningar,
e-postutskick, bokforingsposter eller underlag har andrats.

**Inte klart som enda bokforingssystem:** fullt ore-stod, historiska reskontrasaldon,
transaktionsvis bankmatchning och prov med riktig backup/ingangsbalanser aterstar.
Reskontrans asOfDate styr idag alder men aterstaller inte historiskt saldo.
Se [supplier-payment-safety.md](supplier-payment-safety.md) och
[go-live-riskregister.md](go-live-riskregister.md).

## Rapportskydd 2026-09-09

`npm run test:integration` passerade med 248 enhetstester och 106 integrationstestfall:
inga failures, errors eller hoppade tester. Detta steg lade till 26 enhetstestfall
och 15 HTTP-testfall mot isolerad PostgreSQL, jamfort med foregaende avsnitt.

Verifierat: ackumulerade belopp och differenser kan inte sla runt till fel tecken
i centrala rapporter; negativa resultat bevaras; overskriden beloppsgrans ger
HTTP 422 och REPORT_AMOUNT_LIMIT. Misslyckade HTTP-rapporter/exporter skapar inga
journalposter eller export-audithandelser. Periodlasning upptacker obalans aven
nar en gammal int-summering skulle gett lika debet och kredit.

`npm run test:reports` passerade 19 frontendtestfall for giltiga rapporter,
negativa resultat, felaktig JSON, felpayload och ogiltiga belopps-/radtyper.
Testkommandot ar tillagt i GitHub Actions frontendjobb, men andringarna ar lokala
och har INTE pushats eller verifierats i GitHub Actions i detta steg.

`npm run check:release` passerade inklusive produktionsbuild och webblasarens
runtime-smoke. Gate-korningen omfattar manga statiska kontroller; dessa ersatter
inte integrationstest eller granskning av riktig bokforing. Docker-produktionsbilder
byggdes inte i detta steg. Testdatabas/container avvecklades efter integrationstesten.

Frontend svarar HTTP 200 pa http://localhost:5157. CloudshopApplication maste startas
om for att den riktiga lokala backendprocessen ska ladda andringarna. Riktig
bokforing och historiska underlag har inte andrats.

**Inte klart som enda bokforingssystem:** skydden stoppar belopp utanfor nuvarande
kapacitet men implementerar inte ore-stod. Folj kvarvarande beloppsmigrering,
granskning av ovriga modulers summering, historisk avstamning och verkligt backup-/
betalningsprov i [money-safety.md](money-safety.md).

## Beloppshardning 2026-09-09

Slutkorningen av `npm run test:integration` passerade med 222 enhetstester och
91 integrationstestfall: inga fel, inga errors och inga hoppade tester.
Det ar 37 nya testfall jamfort med Stripe-hardningens 203 + 73. Den exakta
berakningshjalpen jamfors dessutom mot BigDecimal i 2 000 deterministiska fall.

Tester omfattar stora momsmellanprodukter, delbetalning/aterbetalning,
leverantorsmoms, overflow i fakturapris/antal/total, negativ kostnadsmoms,
decimalinput som tidigare kunde kapas, kredit efter prisandring samt
overslag i fler-radiga verifikationer och ingangsbalanser. HTTP-fel kontrolleras
mot den isolerade PostgreSQL-databasen sa att ogiltiga anrop inte sparar bokforing.

`npm run check:release` passerade pa slutversionen inklusive produktionsbuild
och webblasarens runtime-smoke. Testcontainrarna avvecklades. Frontendens lokala
testserver startades pa port 5157 och gav HTTP 200. Riktig bokforing andrades inte.
Backend maste startas om for att ladda andringarna i den vanliga utvecklingsmiljon.
Andringarna ar lokala; ingen ny push eller GitHub-korning ar verifierad har.

Detta ger INTE fullt ore-stod eller ett go-live-godkannande.
[Beloppssakerhet och kvarvarande migrering](money-safety.md) beskriver exakt vad
som fortfarande blockerar skarp anvandning, inklusive rapporternas int-summeringar.

## Backup och kvittoaterlasning 2026-09-09

`npm run test:backup`: 11/11 dynamiska tester godkanda med verklig PostgreSQL 16,
pg_dump, pg_restore och kopiering/hashkontroll av syntetiska kvittofiler.
Provar lyckad aterlasning, saknad/skadad fil, saknad hash, flyttad Windows-sokvag,
skydd mot lasning utanfor backupmappen, obalanserad verifikation och skadad dump.
Tre av fallen kor deployskriptet med simulerad Docker: gammal oskyddad container
blockeras, befintlig kvittovolym och nyinstallation tillats.
`scripts/backup-scripts-test.ps1`: 7/7 tester godkanda med simulerade Docker-exitkoder.
Inga riktiga databaser, kvitton eller nycklar anvandes. Alla testcontainrar avvecklades.

`npm run check:release` passerade inklusive produktionsbuild och browser-smoke.
`check:backup`, `check:ci`, `check:docker` samt shell-syntaxkontroll passerade.
Backendens Maven-tester och produktions-imagebyggen kordes inte om i detta steg;
inga Java-kallfiler andrades. Den nya backupverifieringen kor riktig PostgreSQL.

Produktions-compose lagrar nu uploads i en beststandig volym. Deployskriptet
stoppar uppgradering av gamla containrar utan denna volym tills filer migrerats.
Backupskripten stoppar vid fel och skiljer katalogkontroll fran provad aterlasning.
GitHub Actions har ett nytt obligatoriskt backup/restore-jobb fore Docker build;
en korning pa GitHub av dessa andringar ar annu inte verifierad.

Detta ar INTE ett go-live-godkannande. Kvar: riktig databas+filbackup fran samma
tidpunkt och provade appfloden efter aterlasning, komplett hantering av oren,
Stripe-flode med granskat momsunderlag och verifierade ingangsbalanser/rapporter.
Se [backup-runbook](backup-restore-runbook.md). Gamla absoluta kvittosokvagar
migreras inte automatiskt; de rapporteras som `relocatedPaths`.

## Stripe-hardning 2026-09-09

`npm run test:integration` passerade med 203 enhetstester och 73 integrationstestfall,
utan fel eller hoppade tester. De 19 nya fallen anvander syntetiskt signerade
Stripe-handelser och riktig isolerad PostgreSQL. De provar faktiskt delbetalt
belopp, avrakningskonto 1580, kontantmetoden, ogiltiga belopp/valutor, fordrojd
betalning, event- och sessionsdubbletter, samtidighet med manuella betalningar,
rollback/retry och avvisning av externa kop utan granskat fakturaunderlag.

`npm run check:release` passerade inklusive build och browser-smoke. Inga riktiga
betalningar, Stripe API-anrop eller kundmejl anvandes i dessa tester.
GitHub Actions verifieras efter push. [Kontrakt och skarpa blockerare](stripe-booking-safety.md).

CI-uppfoljning: frontend-smoke laste en annu tom sida efter en fast vantetid.
Smoke vantar nu upp till 25 sekunder pa monterad auth-vy eller crash-fallback,
aven efter reload. De befintliga kontrollerna for blank sida, inloggningsskydd
och aterstallning behalls. Lokal `npm run smoke:runtime` passerade efter fixen.
En separat CI-starttimeout i system-Chrome ledde till att CI nu installerar
versionslast Chrome Headless Shell 153.0.8010.36 fran Googles Chrome for Testing.
Detta paverkar inte anvandarens lokala Chrome-installation.

## Betalningskontroller 2026-09-09

`npm run test:integration` passerade med 203 enhetstester och 54
integrationstestfall: inga fel och inga hoppade tester. De 34 nya fallen provar
ogiltiga belopp, standardbelopp, datum, saknad faktura, samtidiga manuella
betalningar/aterbetalningar/krediteringar och strikt JSON-inlasning via HTTP.
Testdatabas och containrar avvecklades efter korningen.

`npm run check:release` passerade for frontendandringen, inklusive build och
webblasarsmoke. De nya integrationstesterna kors av befintlig CI-profil.
GitHub Actions for denna andring ska kontrolleras efter push.

Detta ar inte ett godkannande for skarp automatisk Stripe-bokforing eller belopp
med oren. Se kvarvarande risker i [databastesternas omfattning](database-integration-tests.md).

## Databasverifiering 2026-09-09

`npm run test:integration` passerade med 203 enhetstester och 20 integrationstester,
utan fel eller hoppade tester. Det tidigare tomma `contextLoads`-testet har ersatts
med riktig Spring Boot-start, PostgreSQL 16 och HTTP-kontroll. Testerna provar
rollback vid databas- och revisionsloggsfel samt samtidiga verifikationsnummer.
Testdatabasen och containrarna avvecklades efter korningen.

`node --test scripts/git-remote.test.mjs` passerade 10/10 fall. Tva CI-kontroller
kravde tidigare exakt HTTPS-adress med `.git`; nu accepteras samma repository
aven utan suffix och via SSH. GitHub Actions for den nya versionen ska verifieras
efter push. Se [databastesternas omfattning](database-integration-tests.md).

`npm run check:release` passerade ocksa 2026-09-09, inklusive frontend-build,
runtime-smoke och samtliga befintliga releasekontroller. Dockerbyggen och GitHub
Actions for denna andring verifieras separat efter push.

Uppfoljning 2026-09-09: GitHub-backendjobbet for `c5142e4` passerade inklusive
integrationstester. Frontendjobbet naddes fram till browser-smoke men kunde inte
ansluta till Chromes debugport. Smoke-testet vantar nu pa browserns beredskap och
visar startdiagnostik. Vite startas direkt som en agd process, sa att testet kan
avslutas utan kvarvarande npm/Vite-processer. Hela frontend-releasekontrollen
passerade darefter i en ren Linux-klon med Chromium. Windows-smoke verifierades
ocksa fran en tom port med automatisk start och avstangning av Vite.

Datumen nedan avser tidigare fullstandiga releasekorningar:

Senast komplett lokalt releasebevis: 2026-09-07 21:48 +02:00.
Senast standard-release och backendtest verifierat: 2026-09-07 21:48 +02:00.
Senast standard-release efter CI-maintenance verifierat: 2026-08-29 22:07 +02:00.
Senast standard-release efter MVP-slutspurt verifierat: 2026-08-30 18:49 +02:00.

## Kommandon som ska vara grona fore push/deploy

Kor fran projektets rotmapp:

```bash
npm run check:release:full
npm run check:bundle
npm run check:frontend-hygiene
npm run check:secrets
npm run check:dependencies
npm run check:env-go-live
npm run check:ci-handoff
npm run check:post-push
npm run check:startklar
npm run check:mvp-use
npm run check:finish-line
npm run check:operations
npm run check:use-today
npm run check:first-real-data
npm run check:pilot
npm run check:calculations
npm run check:retention
npm run check:audit-integrity
npm run check:period-close
npm run check:handoff
npm run check:go-live-decision
npm run check:external-go-live
npm run check:release-traceability
npm run check:migrations
npm run check:schema-bootstrap
npm run check:git
npm run check:sync
npm run check:prepush -- --allow-ahead
```

Detta bevisar lokalt att:

- frontend bygger for produktion
- produktionsbundlen haller MVP-budget och tunga visuella paket ligger i separata chunks
- frontend smoke-test kan rendera appen och kontrollerar att utloggad auth/sprak bara syns pa startsidan
- viktiga frontend/backend API-kontrakt finns kvar
- frontendens produktionskod saknar gamla demo-filer, fristaende landing page-experiment och `alert()`-anrop
- backend-konstruktorer och Java-records matchar testerna
- dokumentation, secrets, Docker-konfig, vyer och produktionsmallar passerar kontroller
- riktiga API-nycklar for Stripe, HF, Google, OpenRouter och OpenAI-liknande providers stoppas av `check:secrets`
- go-live-miljo variabler for JWT, CORS, RDS, schema, Stripe, SMTP och AI ar dokumenterade och kontrollerade
- frontend dependency-lockfile, buildverktyg och Docker-installation kontrolleras statiskt
- CI-handoff efter push ar dokumenterad med GitHub Actions-jobb, Dockerhub-secrets, jobbnamn och felsokning
- Dependabot bevakar frontend npm, backend Maven och GitHub Actions sa beroenderisker inte bara kontrolleras manuellt
- post-push-verifiering skiljer lokal release fran GitHub-sync, Actions, Dockerhub och externa go-live-bevis
- release-version, commit, Dockerhub-taggar och EC2 `IMAGE_TAG` gar att sparas som releasebevis
- startup-schema-patchar ar speglade i kontrollerad SQL-migration innan RDS-deploy
- forsta RDS-basschema har en kontrollerad schema-bootstrap-runbook
- destruktiva raderingar/reset-endpoints har JWT, feature flags, audit och periodlasningsskydd dar det kravs
- revisionsspar har SHA-256-kedja, auditstampel, CSV-export, backupkoppling och backendtest mot andrad historik
- periodstangning har backendkontroll for blockerare, varningar, attest, bankavstamning, momsbevis, periodstampel och slutlig kedjekod
- redovisningspaket kan exportera SIE, kvittenser, huvudbok, saldobalans, rapporter, kontroller och arsarkiv for saker konsultoverlamning
- slutligt go-live-beslut skiljer lokal MVP fran skarp drift och kraver externa bevis innan riktig kunddata
- forsta riktiga data-grinden stoppar om backup, restore drill, testdata, foretagsinstallningar, nummerserier, personuppgifter, betalningsrutin eller export inte ar kontrollerade
- pilotdrift-grinden begransar forsta riktiga veckan till fa kunder, daglig backup, daglig avstamning, manuella klickbevis och tydliga stoppregler
- externa go-live-bevis for GitHub sync, Actions, Dockerhub, EC2/RDS, schema, backup/restore, Stripe, SMTP, bank/Swish/kort, AI och redovisningspaket ar dokumenterade
- backendtester passerar
- backend Docker-image kan byggas
- frontend Docker-image kan byggas

## Senaste lokala bevis

- `npm run check:prepush -- --allow-ahead`: passed 2026-09-07 21:48 +02:00. Full lokal pre-push-kedja passerade med frontend build, bundle 9/9, professionell loop 20/20, SpeedLedger-paritet 28/28, acceptans 18/18, API-kontrakt 22/22, readiness 158/158, evidence 94/94, use-today 34/34, first-real-data 34/34, pilot 25/25, calculations 46/46, retention 23/23, audit-integrity 25/25, period-close 30/30, handoff 29/29, finance UI 100/100, backendtester 204 tests / 0 failures / 0 errors, Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`, samt ren lokal Git-status for viktiga AliBooks-filer. `--allow-ahead` anvandes avsiktligt eftersom branch ligger fore `origin/main` och nasta externa bevis ar push + GitHub Actions.
- `npm run check:release` och `npm run test:backend`: passed 2026-09-07 21:44 +02:00 efter att viktigaste bokforingsmenyn och snabbstarten pa Oversikt verifierades. Standard release gate passerade med frontend build, runtime smoke, vykontroll 49/49, readiness 158/158, evidence 94/94, use-today 34/34, first-real-data 34/34, pilot 25/25, calculations 46/46, retention 23/23, audit-integrity 25/25, period-close 30/30, handoff 29/29, finance UI 100/100 och release traceability 14/14. Backendtester passerade separat via Docker Maven: 204 tests / 0 failures / 0 errors. Traceability varnar korrekt att branch ligger fore `origin/main` och maste pushas innan GitHub Actions kan bevisa senaste versionen.
- `npm run build`, `npm run check:views`, `npm run check:use-today`, `npm run check:evidence`: passed 2026-09-07 21:32 +02:00 efter att viktigaste bokforingsmenyn flyttades hogst upp efter inloggning och Oversikt fick snabbstart till centrala arbetsytor. `check:views` visar 49/49 menyvyer, `check:use-today` visar 34/34 med ny kontroll for synliga professionella huvudval och snabbstart pa Oversikt, och `check:evidence` visar 94/94.
- `npm run check:release`: passed 2026-09-01 19:14 +02:00 efter Startklar-panel for daglig lokal MVP-anvandning, standard release gate med frontend build, runtime smoke, vykontroll 49/49, readiness 158/158, evidence 94/94, use-today 32/32, first-real-data 34/34, finish-line 21/21, finance UI 100/100 och release traceability 14/14. Traceability varnar korrekt att branch ligger fore `origin/main` och maste pushas innan GitHub Actions kan bevisa senaste versionen.
- `npm run check:use-today`: passed 2026-09-01 19:10 +02:00 efter Startklar-panel for daglig lokal MVP-anvandning, 32/32 inklusive synlig Startklar-knapp nara Oversikt, UI-grind for "Kan jag jobba i AliBooks idag?", vit sida/render recovery, backend/databas, berakningar, verifikat, backup, manuell MVP-kontroll, betalningsrutin, sakerhet och produktionsgrans.
- `npm run check:release:full`: passed 2026-08-30 18:56 +02:00 efter MVP-slutspurt-steget, full release gate med frontend build, bundle 9/9, professionell loop 20/20, SpeedLedger-paritet 28/28, acceptans 18/18, readiness 158/158, evidence 94/94, finish-line 20/20, finance UI 100/100, vykontroll 49/49, runtime smoke, backendtester 204 tests / 0 failures / 0 errors och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`.
- `npm run check:release`: passed 2026-08-30 19:23 +02:00 efter Startklar-panel for forsta riktiga data, standard release gate med frontend build, runtime smoke, vykontroll 49/49, readiness 158/158, evidence 94/94, finish-line 21/21, first-real-data 34/34, finance UI 100/100 och release traceability 14/14. Traceability varnar korrekt att branch ar 72 commits fore `origin/main`.
- `npm run check:finish-line`: passed 2026-08-30 19:13 +02:00 efter Startklar-panel for MVP-slutspurt, 21/21 inklusive UI-bevis for 20-stegslistan.
- `npm run check:first-real-data`: passed 2026-08-30 19:18 +02:00 efter Startklar-panel for forsta riktiga data, 34/34 inklusive UI-grind for riktiga kunder, fakturor, kvitton, bankrader och bokforingsposter.
- `npm run check:release`: passed 2026-08-29 22:07 +02:00 efter Dependabot/CI-maintenance-steget, standard release gate med frontend build, runtime smoke, vykontroll, readiness 154/154, CI 39/39, evidence 91/91, env-go-live 39/39 och externa go-live-bevis 29/29.
- `npm run check:calculations`, `npm run check:mvp-use`, `npm run check:use-today`, `npm run check:speedledger-parity`, `npm run check:go-live-risks`, `npm run check:external-go-live`: passed 2026-08-29 22:07 +02:00 som extra snabbkontroll av professionell MVP-kärna efter commit `940cf4f`.
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-28 22:28 +02:00, AliBooks pre-push gate passed, full release gate, runtime smoke, backendtester 204 tests / 0 failures / 0 errors, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`.
- `npm run check:release:full`: passed 2026-08-28 22:28 +02:00 via pre-push gate, frontend build, runtime smoke, backendtester 204 tests / 0 failures / 0 errors och Docker image builds.
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-27 19:25 +02:00, full release gate, runtime smoke, backendtester 204 tests / 0 failures / 0 errors, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`.
- `npm run check:release:full`: passed 2026-08-27 19:25 +02:00 via pre-push gate, frontend build, runtime smoke, backendtester och Docker image builds.
- `npm run check:bundle`: 9/9, passed 2026-08-27, frontend production bundle budget och separata visual/motion/animation chunks verifierade.
- `npm run test:backend`: passed 2026-08-26 14:33 +02:00, 204 tests, 0 failures, 0 errors
- `npm run check:frontend-hygiene`: passed 2026-08-26, inga demo-filer, fristaende landing page-experiment eller `alert()`-anrop i frontendens produktionskod
- `npm run check:release`: passed 2026-08-26, standard gate fran projektroten med frontend build, frontend-hygien, runtime smoke och alla lokala MVP-kontroller
- `npm run check:secrets`: passed 2026-08-26, inga riktiga Stripe-, HF-, Google-, OpenRouter-, OpenAI-liknande, GitHub-, JWT- eller private-key-hemligheter hittades i tracked project files.
- `npm run check:audit`: passed 2026-08-26, 0 vulnerabilities for frontend production dependencies
- `npm run doctor -- --soft`: passed 2026-08-26, PostgreSQL, backend `/health`, backend `/system/status`, database connection and frontend `5157` OK; warnings kvar for lokal `JWT_SECRET` och Docker-behorighet i sandbox.
- `npm run smoke:runtime`: passed 2026-08-26, frontend renderar utan vit sida eller render recovery.
- Rotkommandon verifierade 2026-08-22 21:43 +02:00: `npm run build`, `npm run check:docs`, `npm run check:release` och `npm run test:backend` fungerar fran projektets huvudmapp.
- `npm run test:backend`: passed 2026-08-24, 204 tests, 0 failures, 0 errors
- `npm run check:release`: passed 2026-08-24, standard gate fran projektroten med 18/18 acceptans, 150/150 readiness, 82/82 evidence, 57/57 data safety, 44/44 production readiness, 39/39 env-go-live, 25/25 pilotdrift, 23/23 retention, 25/25 audit-integritet, 30/30 periodstangning, 29/29 redovisningspaket, 17/17 go-live-beslut, 29/29 externa go-live-bevis, 29/29 post-push-verifiering, 33/33 forsta-riktiga-data och runtime smoke
- `npm run check:startklar`: passed 2026-08-24, 20/20, lokal MVP redo enligt kort Startklar-kontroll. Skarp produktion vantar pa GitHub sync, Dockerhub, EC2/RDS, restore drill, Stripe och SMTP.
- `npm run check:env-go-live`: 39/39, go-live-miljo for JWT, lokal JWT-generator, CORS, RDS, schemaflaggor, Stripe, SMTP, AI-nycklar och hemlighetshantering.
- `npm run check:mvp-use`: 20/20, 20-stegs kontroll for anvandningsklar lokal MVP.
- `npm run check:operations`: 23/23, drift-runbook, incidentlogg, releasejournal och rollback-kontroll.
- `npm run check:use-today`: 34/34, slutligt lokalt anvandningsbeslut med synlig Startklar-knapp, viktigaste bokforingsmenyn, snabbstart pa Oversikt, stoppsignaler, vit-sida/render recovery, Startklar-UI och produktionsblockerare.
- `npm run check:first-real-data`: 34/34, forsta riktiga data-grind for backup, restore drill, testdata, foretagsinstallningar, nummerserier, personuppgifter, betalningsrutin och export.
- `npm run check:pilot`: 25/25, begransad pilotdrift for forsta veckan med daglig rutin, backup, restore drill, manuell klickkontroll, export och stoppregler.
- `npm run check:calculations`: 46/46, berakningsintegritet for faktura, moms, delbetalning, Stripe, leverantorer, verifikat och lon-MVP.
- `npm run check:retention`: 23/23, arkiv och andringsspar for fakturor, kunder, leverantorsfakturor, kvitton, bankreset, periodlasning, hard delete, rattelser och backendtester.
- `npm run check:audit-integrity`: 25/25, revisionsspar, SHA-256-kedja, auditstampel, CSV-export, backupkoppling, JWT-krav och backendtester som visar att andrad historik ger ny fingerprint.
- `npm run check:period-close`: 30/30, periodstangning, blockerare, varningar, attest, bankavstamning, momsbevis, sena verifikat, periodstampel och slutlig kedjekod.
- `npm run check:handoff`: 29/29, redovisningspaket, SIE, SIE-kvittens, resultat, balans, huvudbok, saldobalans, moms, bank, reskontra, arsarkiv, systemdokumentation och saker delning.
- `npm run check:go-live-decision`: 17/17, slutligt beslut for lokal MVP kontra skarp drift, externa bevis, backup/restore, Stripe, SMTP, release-sparbarhet och redovisningskonsult-export.
- `npm run check:external-go-live`: 29/29, externa bevis for GitHub sync, Actions, Dockerhub, EC2/RDS, schema, backup/restore, Stripe, SMTP, bank/Swish/kort, AI och redovisningspaket.
- `npm run check:post-push`: 31/31, post-push-verifiering for GitHub sync, Actions, CI-artifacts/summaries, Dockerhub, sparbara image-taggar och strikt efter-push-lage.
- `npm run check:release:full`: passed 2026-08-24, frontend build, runtime smoke, backendtester 204 tests / 0 failures / 0 errors, release gate och Docker image builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-24 12:14 +02:00, AliBooks pre-push gate passed, backendtester 194 tests / 0 failures / 0 errors, runtime smoke, release gate, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-22 21:58 +02:00, AliBooks pre-push gate passed, backendtester, runtime smoke, release gate, git-clean check och Docker builds for `alibooks-backend:release-gate` och `alibooks-frontend:release-gate`
- `npm run check:release:full`: passed 2026-08-17 09:59 +02:00, frontend build, runtime smoke, backendtester och Docker image builds
- `npm run check:release`: passed 2026-08-16 23:37 +02:00, standard gate med schema-migration, schema-bootstrap och git-skydd for `db/`
- `npm run check:dependencies`: passed 2026-08-17 09:54 +02:00, 32/32
- `npm run check:secrets`: passed 2026-08-24, tidigare hemlighetskontroll utan fynd.
- `npm run check:ci-handoff`: 34/34, GitHub Actions-jobb, artifacts/summaries, Dockerhub workflow, secrets, push/sync-steg, vanliga CI-fel och go-live-grans.
- `npm run check:ci`: 39/39, GitHub Actions kontrollerar backend med `mvn -B test`, PostgreSQL 16, Java 21, frontend release gate, Docker image builds, artifacts/summaries, Dependabot och timeout-skydd.
- `npm run check:audit`: passed 2026-08-17 09:54 +02:00, tidigare audit med 0 vulnerabilities
- `npm run check:release-traceability`: passed 2026-08-22, 14/14, warning: local commits pending push
- `npm run test:backend`: passed 2026-08-17 09:58 +02:00, 190 tests, 0 failures, 0 errors
- `npm run check:prepush -- --allow-ahead`: passed 2026-08-09 19:30 +02:00
- `check:ready`: 158/158
- `check:acceptance`: 18/18
- `check:evidence`: 94/94
- `check:bundle`: 9/9
- `check:data-safety`: 57/57
- `check:prod`: 45/46 (example env has one expected warning for the placeholder owner setup key)
- `check:env-go-live`: 41/41
- `check:ci`: 39/39
- `check:ci-handoff`: 34/34
- `check:post-push`: 31/31
- `check:external-go-live`: 29/29
- `check:schema`: 18/18
- `check:migrations`: 5/5, passed 2026-08-16
- `check:schema-bootstrap`: 10/10
- `check:go-live-risks`: 20/20
- `check:go-live-decision`: 17/17
- `check:manual-go-live`: 18/18
- `check:mvp-use`: 20/20
- `check:operations`: 23/23
- `check:use-today`: 35/36
- `check:first-real-data`: 37/37
- `check:pilot`: 25/25
- `check:calculations`: 46/46
- `check:retention`: 23/23
- `check:audit-integrity`: 25/25
- `check:period-close`: 30/30
- `check:handoff`: 29/29
- `check:speedledger-parity`: 28/28
- `check:startklar`: 20/20
- `npm run check:backup`: passed, 19/19
- `check:finish-line`: 21/21
- Docker images skapade lokalt 2026-08-24 15:49 +02:00:
  - `alibooks-backend:release-gate`
  - `alibooks-frontend:release-gate`
- CI-konfigurationen kontrolleras lokalt med `npm run check:ci` och ingar i release-gaten. GitHub Actions kor ocksa `npm run check:audit` innan frontend release-gate, sparar `backend-surefire-reports` samt `frontend-dist` som artifacts, och Dependabot bevakar frontend, backend och workflow-beroenden.
- CI-handoff efter push kontrolleras lokalt med `npm run check:ci-handoff` och dokumenteras i [ci-handoff-efter-push.md](ci-handoff-efter-push.md), sa GitHub Actions-jobb, artifacts/summaries, Dockerhub-secrets, push/sync och vanliga CI-fel inte tappas bort.
- Post-push verifiering kontrolleras lokalt med `npm run check:post-push` och strikt efter push med `npm run check:post-push -- --require-pushed`, sa lokal MVP inte blandas ihop med GitHub/CI/Dockerhub-bevis.
- MVP-acceptans kontrolleras lokalt med `npm run check:acceptance` och skiljer automatiskt bevis fran manuella go-live-klicktester.
- Runtime-smoke kontrollerar att utloggad startsida visar kompakt login/register/sprak, och att dessa kontroller inte foljer med till andra menyvyer som Kunder.
- CI kor backendtester med `mvn -B test` och explicit `SPRING_JPA_HIBERNATE_DDL_AUTO=update` for testdatabasen, medan produktion defaultar till `validate` och `APP_SCHEMA_PATCH_ENABLED=false` sa RDS-schema inte andras automatiskt.
- Backup/restore-rutinen kontrolleras lokalt med `npm run check:backup` och ingar i release-gaten.
- Restore drill har skyddade script for Linux/EC2 och Windows som kraver `RESTORE_CONFIRM=RESTORE_TO_TEST_DATABASE`.
- Git release status kan kontrolleras med `npm run check:git` innan commit och `npm run check:git -- --strict` efter commit.
- GitHub sync kan kontrolleras med `npm run check:sync` efter push. Den failar om lokala commits inte finns pa GitHub.
- Go-live-risker foljs i [go-live-riskregister.md](go-live-riskregister.md) och kontrolleras lokalt med `npm run check:go-live-risks`.
- Slutligt go-live-beslut foljs i [go-live-beslut.md](go-live-beslut.md) och kontrolleras lokalt med `npm run check:go-live-decision`.
- Manuella externa go-live-bevis kontrolleras lokalt med `npm run check:manual-go-live`, sa PDF, SMTP, Stripe, bank/betalningsflode, backup/restore, publik URL och redovisningskonsult-export inte tappas bort.
- Anvandningsklar lokal MVP kontrolleras med `npm run check:mvp-use`, som samlar 20 praktiska steg fran lokal start till go-live-beslut.
- Driftberedskap kontrolleras med `npm run check:operations`, sa Driftcenter, incidentlogg, releasejournal, backup/smoke-test och rollback-plan inte tappas bort.
- Sista lokala anvandningsbeslutet kontrolleras med `npm run check:use-today`, sa AliBooks visar nar lokal MVP kan anvandas och nar arbetet ska stoppas innan viktig data registreras.
- Forsta-riktiga-data-skydden kontrolleras med `npm run check:first-real-data`; ett godkant statiskt test ar inte go-ahead for verkliga poster. Fullt ore-stod, backup/restore, testdata, foretagsinstallningar, nummerserier, personuppgifter, betalningsrutin och export maste verifieras separat.
- Berakningsintegritet kontrolleras med `npm run check:calculations`, sa faktura, moms, delbetalning, Stripe, leverantorer, verifikat, rapporter och lon-MVP inte tappar sina skydd.
- Arkiv och andringsspar kontrolleras med `npm run check:retention`, sa hard delete, kvittoersattning, kundhistorik, leverantorsfakturor, periodlasning och rattelsefloden inte tappar sina skydd.
- Revisionsspar-integritet kontrolleras med `npm run check:audit-integrity`, sa auditkedja, auditstampel, CSV-export, backupkoppling och tamper-kansligt backendtest inte tappar sina skydd.
- Periodstangning kontrolleras med `npm run check:period-close`, sa blockerare, varningar, attest, bankavstamning, momsbevis, sena verifikat, periodstampel och slutlig kedjekod inte tappar sina skydd.
- Redovisningspaket kontrolleras med `npm run check:handoff`, sa SIE, kvittens, huvudbok, saldobalans, rapporter, kontrollbevis, arsarkiv och saker konsultdelning inte tappas bort.
- SpeedLedger-liknande funktionsparitet kontrolleras med `npm run check:speedledger-parity`, sa AliBooks inte overdriver extern bankkoppling, PEPPOL/e-faktura, NE-inlamning, arsredovisning, E-dagsavslut, factoring eller fullservice.
- Pre-push-kontrollen `npm run check:prepush -- --allow-ahead` kor full release gate och ren Git-status innan sjalva pushen. Utan `--allow-ahead` kraver den aven att GitHub redan ar i sync.
- Databasschema-lage kontrolleras med `npm run check:schema` sa `SPRING_JPA_HIBERNATE_DDL_AUTO` och `APP_SCHEMA_PATCH_ENABLED` ar explicita lokalt och produktion defaultar till `validate` plus avstangd startup-patch.
- Kontrollerad schema-migration kontrolleras med `npm run check:migrations`. Filen `db/migrations/001_startup_schema_patch.sql` speglar `DatabaseSchemaPatch` och ska testas mot restore/staging innan RDS-deploy.
- Forsta RDS-basschema kontrolleras med `npm run check:schema-bootstrap` och dokumenteras i [schema-bootstrap-runbook.md](schema-bootstrap-runbook.md).
- Frontend-beroenden och buildverktyg kontrolleras lokalt med `npm run check:dependencies`. Fore skarp deploy ska aven aktuell online-audit koras med `npm run check:audit` eller striktare.
- Release-sparbarhet kontrolleras med `npm run check:release-traceability`: package-version, git branch/commit, Dockerhub `sha-*`/`v*`-taggar och EC2 `IMAGE_TAG`.

## Kvar fore riktig go-live

Detta maste fortfarande verifieras utanfor lokal maskin innan AliBooks anvands som riktig produktionsapp:

- GitHub Actions ska vara gron efter push.
- Efter commit ska `npm run check:git -- --strict` visa att inga viktiga AliBooks-filer ligger kvar utanfor git.
- Efter push ska `npm run check:sync` visa att lokal branch och GitHub ar i sync.
- Dockerhub workflow ska pusha backend/frontend images.
- EC2 ska kora `docker compose -f docker-compose.prod.yml up -d` mot riktig RDS.
- Pa EC2 ska strikt produktionskontroll koras med riktig, ej committad `.env`:

```bash
npm run check:prod -- --env-file ../.env --strict
```

- Produktions-smoke ska visa att publik frontend, backend health och databas fungerar.
- Backup ska vara verifierad innan riktig kunddata och bokforingsdata flyttas in.
- En restore drill ska goras till separat test database innan produktion anvands skarpt.
- RDS-schema ska uppdateras kontrollerat med testad schema-dump plus `db/migrations/001_startup_schema_patch.sql`, inte via automatisk startup-patch i produktion.
