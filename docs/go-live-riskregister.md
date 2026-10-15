# AliBooks go-live riskregister

Det har registret skiljer pa lokal MVP-stabilitet och riktig produktionsklarhet.
Den sista lokala anvandningsgransen kontrolleras med `npm run check:use-today`.
Det slutliga beslutet fore skarp drift finns i [go-live-beslut.md](go-live-beslut.md) och kontrolleras med `npm run check:go-live-decision`.
AliBooks far inte anvandas med skarp kunddata, bokforingsdata eller betalningar innan blockerande externa kontroller ar verifierade.
Det finns aven interna blockerare: fullt ore-stod och en bredare beloppsmodell
ar inte fardiga. Centrala rapporter stoppar nu summor utanfor nuvarande grans;
andra modulers summering aterstar. En gron release-gate godkanner inte skarp bokforing.
Se [beloppssakerhet](money-safety.md) for verifierade rattningar och kvarvarande risker.

## Statusnivaer

- `KLAR LOKALT`: Funktionen ar verifierad pa utvecklingsmaskinen.
- `KRAVER EXTERN VERIFIERING`: Funktionen finns i projektet men maste bevisas i GitHub, Dockerhub, AWS, Stripe, SMTP eller med restore drill.
- `BLOCKERAR SKARP DRIFT`: Riktig drift ska vanta tills punkten ar klar.

## Risker fore skarp drift

Bankimportens nya betalnings- och kostnadsfloden sparar bokforing, historik och
audit atomiskt, med serialiserade aterforsok. Detta minskar risken for dubbla och
halvsparade bokningar men ersatter inte historisk avstamning eller bankens egna
transaktionsidentiteter. Gamla timestamp-ID:n migreras inte automatiskt.
Se [bank-import-atomicity.md](bank-import-atomicity.md).

Bankrader har nu sparad unik journalradskoppling. Aldre okopplade rader och
felmatchningar ger kritiska avstamningsfel aven vid noll nettodifferens.
Manuell koppling till befintlig bokforing ar mojlig efter uttryckligt val,
utan att skapa nya betalningar. Detta loser inte fullstandigheten hos utdrag,
klumpsummor eller ingangsbalanser. Se [bank-journal-links.md](bank-journal-links.md).

| Omrade | Status | Risk | Kontroll | Bevis som kravs |
| --- | --- | --- | --- | --- |
| MFA och administrativ atkomst | BLOCKERAR SKARP DRIFT | TOTP, reservkoder och krav pa MFA finns och testas lokalt, men aktiv MFA pa det verkliga agarkontot och driftens fjarratkomst ar inte verifierade. Ett kapat konto kan annars ge atkomst till redovisningsdata. | Aktivera MFA for agare/admin, skydda driftkonton separat och prova enrollment, aterstallning, audit och att MFA inte kan kringgas i produktionsmiljon. | Daterat test av agarkontots MFA, reservkod/aterstallning, avregistrering och fjarratkomst samt signerad tillgangsgranskning. |
| NIS2/cybersakerhetslagen scope | KRAVER EXTERN VERIFIERING | SME-storlek ensam avgor inte om cybersakerhetslagen omfattar verksamheten. Felaktigt antagande kan leda till missad styrning och incidentrapportering. | Dokumentera sektor/tjanst, storlekskriterier, undantag, slutsats och omprovningsdatum. Bekrafta oklarheter med behorig tillsynsmyndighet eller jurist. | Daterad scopebedomning med faktaunderlag och ansvarig beslutsfattare; rapporteringsvag identifierad om lagen galler. Se [SME-styrningspaket](cybersecurity-governance-sme.md). |
| Ledningens cybersakerhetsansvar | BLOCKERAR SKARP DRIFT | Utan uttryckligt ansvar, resurser, utbildning och dokumenterat riskbeslut blir kontroller och kvarstaende risker agarlosa. | VD/styrelse utser ansvarig, godkanner riskaptit och uppfoljning, far utbildning och signerar riskbeslut. | Faktiskt signerat beslut/protokoll, namngiven agare, resurser, utbildningsbevis och daterad uppfoljning. Se [SME-styrningspaket](cybersecurity-governance-sme.md). |
| Tillgangar och risker | BLOCKERAR SKARP DRIFT | Applikationens generella riskvy ersatter inte verksamhetens kompletta inventering av data, system, agare och leverantorsberoenden. | Halla ett agt och klassat tillgangsregister och en riskbehandlingsplan med atgard, agare och slutdatum. | Daterat verksamhetsregister, granskade risker och uttryckligen accepterade kvarstaende risker. |
| Incidentberedskap och rapportering | BLOCKERAR SKARP DRIFT | Lokal incidentlista ar inte bevis pa en fungerande, bestandelig incidentprocess eller myndighetsrapportering. | Utse incidentansvarig, ova eskalering, bevisbevarande, aterstallning och rapporteringsbeslut. Faststall gallande sektorstidslinje och officiell kanal. | Ovningsrapport och incidentjournal; om lagen galler, visad process for tidig varning inom 24 timmar och rapport inom 72 timmar fran kannedom. |
| Leverantorsrisk | BLOCKERAR SKARP DRIFT | Bokforingsreskontra och integrationsstatus granskar inte leverantorers sakerhet, underleverantorer, incidentkrav eller exit. | Riskgradera host, RDS, e-post, betalning, AI/API, backup och driftkonton; granska avtal, atkomst, incident, kontinuitet och exit. | Daterade bedomningar for kritiska leverantorer, avtal/DPA dar behovligt, ansvarig agare samt fungerande dataexport/exitplan. |
| Kontinuitet och aterstallning | BLOCKERAR SKARP DRIFT | Lokal backupkontroll bevisar inte skyddad separat kopia, uppnad RPO/RTO eller fungerande applikation efter restore. | Faststall RPO/RTO, separat krypterad kopia och kor en isolerad aterlasning med applikationskontroller. | Restoreprotokoll med tidsatgang, kontrollsummor, applikationssmoke, datarelationer, avvikelser och godkannande. |
| Momssats och momsklassificering | BLOCKERAR SKARP DRIFT | Nya fakturor och manuella Stripe-forsaljningar kan anvanda 6/12/25 procent per tjanst/forsaljning, men systemet klassificerar inte automatiskt vilken sats som ska anvandas. Kundfakturor med ore i exakt moms stoppas tills minor-unit-migreringen ar klar. | Bekrafta momshanteringen for varje faktisk tjanst med redovisningskonsult och kontrollera faktura, kredit, delbetalning, aterbetalning och momsrapport per sats. Se vat-rates.md och money-safety.md. | Verifierade underlag och bokforingskonton stammer per momssats; historiska poster ar avstamda och beloppens precision ar godkand. |
| Fakturadokument och utskicksatervinning | BLOCKERAR SKARP DRIFT | Nya utstallda fakturor har immutable snapshot och original-PDF med kontrollsumma. Mejltjansten avvisar rekonstruerade historiska PDF:er och utkast innan SMTP. Bestanding leveransjournal visar `PENDING` och `UNCERTAIN`; autentiserad operator kan avstamma dessa mot extern bevisning som `DELIVERED` eller `NOT_DELIVERED` med revisionsspar. Fakturamejl med olost forsok eller oforklarad saknad skickathistorik blockeras fore omsandning; automatiska paminnelser gor inte blinda aterforsok. SMTP-timeout kan anda betyda leverans. Outbox och SMTP/databas-commit ar inte atomiska, den manuella rutinen ar inte verifierad med verklig SMTP, och aldre dokument kan sakna original. | Bevara originalunderlag, ovningskor avstamning med transportlogg och faktisk mottagare, bygg bestaende outbox/atervinningsflode fore skarp drift. Se invoice-document-snapshots.md och release-evidence.md. | Originaldokument kan aterlasas oforandrade och varje SMTP-timeout/commitfel kan avstammas utan blinda dubbla utskick i ett dokumenterat verkligt leveransprov. |
| Manuella verifikationer och originalunderlag | BLOCKERAR SKARP DRIFT | Nya manuella verifikat kan skapas atomiskt med PDF/bild i databasen, och M/IB utan bilaga markeras som kritiska. Bokforda journalrader kan inte langre andras eller raderas med normal SQL; rattelse kraver nytt verifikat. Nya verifikat maste balansera och forseglas vid commit, men aldre verifikat granskas inte automatiskt av triggern. Aldre verifikat kan sakna underlag; andra filformat och ursprungligt digitalt format tacks inte automatiskt. Aldre flerradiga auditbelopp kan ocksa avvika fran journalens total. | Ga igenom varje historiskt M/IB, komplettera saknade underlag och verifiera originalformat samt aterlasning och SHA-256 i isolerad restore. Kor fullstandig balans- och migreringskontroll mot aterlast data. Kor den autentiserade, skrivskyddade `/audit-events/voucher-amounts` och utred varje avvikelse utan att skriva om auditposter. Kontrollen jamfor befintliga skapelsehandelser med journalen och hittar M/IB-verifikat utan sadan handelse; den bevisar inte att andra verifikatserier har komplett audit. Granska ocksa fler-radiga manuella verifikat och IB-flodet innan skarp anvandning. | Varje verifikat har granskat originalunderlag, kopplingen kan aterlasas efter restore och inga kritiska sparbarhetsavvikelser aterstar. |
| Historisk reskontra och bankmatchning | BLOCKERAR SKARP DRIFT | Faktureringsmetodens 1510/2440 kontrolleras per faktura mot huvudboken. Bankkontrollen kraver nu journalradskoppling, inte bara lika totalsummor. Importerad/raderad historik, ingangsbalanser, klumpsummor och kontantmetodens bokslutsflode aterstar. | Verifiera verkliga underlag och ingangsbalanser, migrera leverantorsbetalningar fran text och granska varje aldre bankkoppling. Periodlasning och journalbokforing har gemensamt databaslas. Se bank-journal-links.md, period-write-serialization.md och subledger-control.md. | Verkliga saldon per rapportdatum stammer med underlag/huvudbok; varje bankrad har sparad koppling och dubblettkontroll samt verifierat originalutdrag. |
| Oren och beloppssummering | BLOCKERAR SKARP DRIFT | Heltals-SEK kan inte bevara alla underlagsbelopp; centrala rapporter avvisar for stora summor, ovriga moduler aterstar att granska. | Genomfor versionssatt beloppsmigrering med databas-, API-, rapport- och importtester. | Underlag med oren och stora sammanlagda saldon bevaras exakt hela vagen, inklusive moms och Stripe. |
| GitHub Actions CI | KRAVER EXTERN VERIFIERING | Lokala tester kan vara grona medan GitHub Actions failar efter push. | Pusha release-commits och kontrollera CI. | Backend build and test, Frontend release gate och Docker build ar green i GitHub Actions. |
| GitHub sync | BLOCKERAR SKARP DRIFT | Lokala commits kan saknas pa GitHub, sa moln och demo kor gammal kod. | Kor `npm run check:sync` efter push. | `check:sync` ar gron och branch ar inte ahead of origin/main. |
| Dockerhub images | KRAVER EXTERN VERIFIERING | EC2 kan inte deploya om images inte finns eller har fel tagg. | Kor Dockerhub workflow manuellt eller via versionstagg. | Backend/frontend images finns i Dockerhub med `latest`, `sha-*` eller `v*` tagg. |
| EC2 deploy | BLOCKERAR SKARP DRIFT | Appen kan fungera lokalt men inte som publik URL. | Kor `scripts/ec2-deploy.sh` pa EC2. | Frontend container, backend container och smoke test ar green pa EC2. |
| RDS databas | BLOCKERAR SKARP DRIFT | Backend startar inte eller skriver mot fel databas. | Kor `npm run check:prod -- --env-file ../.env --strict` pa EC2. | `/api/system/status` visar `database.ok = true` mot RDS. |
| Miljo variabler och CORS | BLOCKERAR SKARP DRIFT | Publik frontend kan prata med fel backend, localhost kan ligga kvar, eller svag JWT kan skydda riktig data for daligt. | Kor `npm run check:env-go-live` och `npm run check:prod -- --env-file ../.env --strict`. | `JWT_SECRET` ar stark, `APP_CORS_ALLOWED_ORIGINS` matchar publik frontend, `APP_CORS_LOCAL_DEV_ENABLED=false`, RDS-env ar satt och hemligheter ligger utanfor GitHub. |
| Konto och arbetsyteisolation | BLOCKERAR SKARP DRIFT | Oppen sjalvregistrering ar stangd efter forsta agarkontot och produktionsstartnyckel kravs. Produktionsstart och produktions-API stoppar nu databaser med fler an ett konto; API nekar ocksa token fran ett borttaget konto. Datamodellen saknar fortfarande roller och tenant-isolering; alla autentiserade konton i samma installation ser samma foretagsdata. | Anvand en enda agare for en lokal/enforetags-MVP. Granska databasens konton vid restore och drift; stoppskyddet ersatter inte separat roll- och foretagsisolering. Lagg inte till medarbetarkonton eller flera foretag innan tenant- och rollskydd finns. | Forsta kontot ar skapat med startnyckel; registrering nekar fler konton, produktionsstart och API nekar flera konton under drift. Separat flerforetags-/rollisolering ar implementerad och testad innan fler anvandare eller foretag tillats. |
| Backup och restore drill | BLOCKERAR SKARP DRIFT | Lokal databas och nio filer aterlastes isolerat 2026-09-09, men nio filer saknar kostnadskoppling och en kostnad saknar kvittoreferens. Skyddad extern kopia och appfloden mot aterlast data ar inte verifierade. | Kor `npm run backup:local` med samtliga skrivare pausade. Granska underlagskopplingar, ordna krypterad separat kopia och prova appens floden i isolerad aterstallningsmiljo. Se backup-restore-runbook.md och release-evidence.md. | Matchande radantal och filhashar finns lokalt. Kompletta underlag, fungerande appaterlasning och aterstallning fran separat lagringsplats aterstar. Enbart `pg_restore -l` ar inte ett aterlasningsprov. |
| Databas-schema och Hibernate | BLOCKERAR SKARP DRIFT | Fel schema kan ge `column does not exist` eller tysta databasandringar om `ddl-auto` eller startup-patchar inte ar valt medvetet. | Kor `npm run check:schema`, `npm run check:migrations` och `npm run check:schema-bootstrap`. Testa schema-dump och `db/migrations/001_startup_schema_patch.sql` mot restore/staging och kontrollera att produktion anvander `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` eller `none` samt `APP_SCHEMA_PATCH_ENABLED=false` fore RDS-deploy. | Schema-lage och schema-patch-flagga ar explicita i `.env`, Docker Compose och production readiness, migrationsfilen speglar backendens startup-SQL, schema-dump ar testad enligt `schema-bootstrap-runbook.md`, och produktion muterar inte RDS-schema automatiskt. |
| Databasrollens privilegier | BLOCKERAR SKARP DRIFT | Om appens RDS-konto ager tabeller eller kan skapa, stanga av triggers eller trunkera tabeller kan ett fel eller ett intrang kringga journalens och auditloggens databassparrar. | Importera schema och kor migrationer med separat agare. Ge appkontot bara nodvandiga data- och sekvensrattigheter enligt `schema-bootstrap-runbook.md`; produktionsstartens `ProductionDatabasePrivilegeGuard` ska kontrollera verkliga privilegier fail-closed. | Isolerad RDS-restore visar att appkontot inte ager tabeller och saknar `CREATE`, `TRIGGER`, `TRUNCATE` och `REFERENCES`; appen startar med sitt separata konto och migrationskontot anvands aldrig av webbappen. |
| Frontend beroenden | KRAVER EXTERN VERIFIERING | Appen kan bygga lokalt men ha stale lockfile, osakra package-specs eller aktuella sarbarheter i npm-ekosystemet. | Kor `npm run check:dependencies` lokalt och `npm run check:audit` med internet fore skarp deploy. | Lockfile/Docker-installation ar gron lokalt och online-audit visar inga hoga eller kritiska produktionsberoenden. |
| Stripe betalningar | KRAVER EXTERN VERIFIERING | Betalningar kan tas emot men inte bokforas korrekt om webhook saknas eller ar fel. | Testa Stripe test-webhook och `checkout.session.completed`. | AliBooks skapar/uppdaterar faktura eller Stripe-forsaljning och bokforingen balanserar. |
| SMTP e-post | KRAVER EXTERN VERIFIERING | Faktura- och paminnelsemail kan se klara ut men inte skickas pa riktigt. | Lagga SMTP i ej committad `.env` och skicka testmail. | Testmail kommer fram och audit/event-historik visar skickad faktura/paminnelse. |
| AI och personuppgifter | BLOCKERAR SKARP DRIFT | Personnummer, adress, telefon eller e-post kan skickas till extern AI av misstag. | Anvand AI-sakert lage och anonymiserad export for analys. | Sakerhetssidan och anonym export visar att PII minimeras eller tas bort. |
| Kort, Swish och kassaregisterregler | KRAVER EXTERN VERIFIERING | Elektroniska betalningar kan krava extra rutin eller kontroll beroende pa saljflode. | Kontrollera med Skatteverket/redovisningskonsult innan skarp kort/Swish/Apple Pay-rutin. | Beslutad rutin ar dokumenterad innan riktig betalningsdrift. |
| Bokforingsansvar | BLOCKERAR SKARP DRIFT | Appen kan rakna ratt i tester men anvandaren ansvarar fortfarande for korrekt bokforing. | Gor manuell rimlighetskontroll och export innan periodlasning/bokslut. | Saldobalans, momsrapport, huvudbok, resultat/balans och arkivexport ar sparade. |

## Minsta go-live-beslut

Innan riktig drift ska dessa vara sant:

1. Lokal full gate ar gron.
2. GitHub Actions ar gron efter push.
3. Dockerhub images finns med vald tagg.
4. EC2/RDS smoke test ar gron.
5. Backup och restore drill ar verifierad.
6. Schema-bootstrap och schema-migration ar testade mot restore/staging och RDS kor med `APP_SCHEMA_PATCH_ENABLED=false`.
7. Stripe och SMTP ar testade om de ska anvandas skarpt.
8. AI anvander minimerad eller anonymiserad data.
9. Betalningsrutinen for kort/Swish/Apple Pay ar kontrollerad.
10. Frontend dependency-audit ar kontrollerad.
11. Release-sparbarhet ar kontrollerad med `npm run check:release-traceability`.
12. En export kan lamnas till redovisningskonsult.
13. MFA ar aktivt och testat for agarkonto och administrativa vagar.
14. NIS2-scope ar dokumenterat utan att dra slutsats enbart fran SME-storlek.
15. Ledningsansvar, tillgangs-/riskregister, incidentovning och kritiska leverantorsbedomningar har daterade bevis.

## Snabbt beslut just nu

En gron release-gate visar lokal teknisk teststatus, inte att AliBooks ar klart som enda bokforingssystem.
AliBooks ar inte skarp produktionsklar for riktig kunddata forran punkterna ovan har externt bevis.
