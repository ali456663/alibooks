# AliBooks cybersakerhetsstyrning for SME

Detta dokument ar en praktisk styrningsmall, inte juridisk radgivning, revision eller ett intyg om NIS2-efterlevnad. Hall verksamhetsbeslut, risker och bevis har eller i ett separat styrt register. Lagg aldrig in losenord, MFA-hemligheter, backupnycklar eller kansliga incidentdetaljer i Git.

## Beslut om NIS2-omfattning

Sverige inforde cybersakerhetslagen (2025:1506) och cybersakerhetsforordningen (2025:1507) den 15 januari 2026. Om en verksamhet omfattas avgors inte av en etikett som "SME" ensam. Bedom verksamhetssektor och tjanst mot lagens bilagor, storlek enligt tillampliga EU-kriterier, verksamhetens betydelse och lagens undantag. Vissa digitala tjanster och andra uttryckligen angivna aktorer har egna regler. Dokumentera aven varfor verksamheten bedoms falla utanfor och nar bedomningen ska omprovas.

Gor bedomningen i denna ordning:

1. Beskriv den juridiska personen, land, SNI-koder, faktiska tjanster och vilka kunder/samhallsfunktioner som ar beroende av dem.
2. Matcha varje faktisk tjanst mot relevant sektor och verksamhet i lagens bilagor; utga inte fran produktnamn eller enbart SNI-kod.
3. Dokumentera antal anstallda, arsomsattning/balansomslutning och eventuell koncernkoppling enligt EU:s SME-definition.
4. Prava lagstadgade undantag, sarskild betydelse, ensam/vasentlig leverantorsroll och eventuella sektorsregler.
5. Registrera slutsats som `omfattas`, `omfattas sannolikt`, `omfattas inte` eller `juridisk bedomning kravs`, med underlag, beslutsfattare, datum och omprovningsdatum.
6. Bekrafta osaker bedomning med behorig tillsynsmyndighet eller svensk jurist. Gissa inte myndighet: kontrollera aktuell ansvarig myndighet for just sektorn.

| Falt | Verksamhetens svar/bevis |
| --- | --- |
| Juridiskt namn och organisationsnummer | Fyll i internt |
| Faktiska tjanster och sektorsmatchning | Fyll i med lagbilaga som underlag |
| Anstallda, omsattning, balans och koncern | Fyll i enligt aktuell SME-definition |
| Undantag eller sarskild betydelse | Motivera eller ange ej bedomt |
| Slutsats och osakerhet | Omfattas / sannolikt / inte / juridisk bedomning kravs |
| Ansvarig beslutsfattare, datum, omprovning | Fyll i; omprova vid andrad verksamhet eller regel |

## VD-/styrelsebrev: beslut om cybersakerhetsansvar

**Till:** Styrelsen och verksamhetsansvariga  
**Fran:** VD/verksamhetsansvarig  
**Datum:** [datum]  
**Amne:** Ansvar, riskbeslut och uppfoljning av informations- och cybersakerhet

Jag faststaller att [namn/roll] ansvarar for att samordna verksamhetens cybersakerhetsarbete och rapportera vasentliga risker, incidenter och avvikelser till ledningen. Respektive system- och informationsagare ansvarar for klassning, atkomst, korrekthet och kontinuitet inom sitt omrade.

Ledningen ska:

- godkanna verksamhetens riskaptit, prioriterade tillgangar, riskbehandling och resurser;
- se till att ansvariga far relevant utbildning och att ledningen sjalv far utbildning;
- krava periodisk rapportering av risker, incidenter, leverantorsberoenden och testade atgarder;
- fatta uttryckligt beslut om kvarstaende risker med agare, motivering och slutdatum;
- kontrollera om verksamheten omfattas av cybersakerhetslagen och faststalla rapporteringsvagar;
- stoppa behandling av riktiga redovisningsdata i AliBooks tills go-live-blockerare ar verifierade.

Detta beslut ar inte en forsakran om NIS2-efterlevnad och overfor inte bort lagstadgat ansvar. Det ska foljas upp minst kvartalsvis och efter allvarlig incident eller vasentlig forandring.

**Beslut/avvikelser:** [fyll i]  
**VD/verksamhetsansvarig:** [namn, datum, signatur]  
**Styrelseprotokoll/referens:** [referens eller ej tillampligt]

## Minimikrav i styrning och operativa kontroller

| Omrade | Minsta arbetssatt | Bevis att spara | Status i AliBooks |
| --- | --- | --- | --- |
| Ansvar och ledningsbeslut | Namngiven ansvarig, tillgangsagare, riskagare, kvartalsvis ledningsuppfoljning och dokumenterade riskbeslut | Signerat beslut, protokoll, atgardslista | Ledningsmall finns; inget undertecknat beslut verifierat |
| Risk och tillgangar | Inventera data, system, konton, integrationer, lagringsplatser, kritikalitet, agare och beroenden; koppla hot/sarbarhet till behandling | Daterat tillgangsregister, riskbedomning, behandlingsplan | Go-live-riskregister finns; lokalt verksamhetsregister maste fyllas och granskas |
| MFA och atkomst | MFA for agare/admin och fjarratkomst; unika konton, minsta privilegium, snabb avregistrering, regelbunden behorighetsgranskning | MFA-policy/status, granskningsdatum, avregistreringsprov | TOTP finns i applikationskoden. Krypteringsnyckel, databaspatch, agarkontoaktivering och staging-/restoreprov aterstar innan kontrollen kan godkannas |
| Backup och kontinuitet | Krypterade, separerade och atkomstskyddade kopior; definiera RPO/RTO; testa hel aterlasning i isolerad miljo; dokumentera alternativ drift | Backup-logg, restore-rapport med tid, kontrollsummor, RPO/RTO och avvikelser | Lokal restore har provats; separat skyddad kopia, appaterlasning och extern restore aterstar |
| Incidenter | Kontaktkedja, severity, bevarad tidslinje/bevis, begransning, aterstallning, efteranalys och rapporteringsbeslut; ova minst arligen | Incidentjournal, ovningsrapport, myndighets-/kundbeslut och tidsstamplar | Lokal incidentlista ar inte ett centralt, bestandeligt incidenthanteringssystem |
| Leverantorer | Inventera leverantor/tjanst, data, underleverantorer, plats, kritikalitet, atkomst, incidentkrav, exit och kontinuitetsalternativ; granska fornyelser/andringar | Leverantorsregister, avtal/DPA, riskbedomning, granskning och exitprov | Leverantorsfakturor ar bokforing; de ar inte en sakerhetsgranskning av leverantorer |
| Utbildning och cyberhygien | Utbilda personal/ledning, phishingrutiner, patchning, enhetsskydd, autentisering och rapporteringsvag | Deltagarlista, datum och ovningsresultat | Verifieras separat; inget utbildningsbevis i denna vy |
| Kryptografi och data | Bestam godkand kryptering, nyckelagare, rotation och gallring for data, backup och hemligheter | Konfiguration, nyckelrotation och gallringsbevis | Produktionsmiljo maste verifieras externt |

## Incidentflode och tidskrav

Alla verksamheter bor ha intern eskalering och loggning aven om de inte omfattas av NIS2. For verksamhet som omfattas av cybersakerhetslagen ska betydande incidenter hanteras enligt lagens rapporteringsregler: tidig varning inom 24 timmar och incidentanmalan inom 72 timmar fran kannedom; slutrapport normalt inom en manad. Lagens sarskilda regel for betrodda tjanster anger kortare tidig varning. Bekrafta aktuell lagtext, sektorsregel, mottagande myndighet och hur klockan startar innan incident hander. En intern anteckning eller kundnotis ersatter inte myndighetsrapportering.

1. Upptack och skapa incident-ID; notera upptackt/kannedomstid, system, agare och initial paverkan.
2. Eskalera till incidentansvarig och ledning; skydda personer, bokforingsdata och bevis.
3. Begransa incidenten utan att forstora loggar; dokumentera alla beslut och tidsstamplar.
4. Bedom omfattning, paverkan, personuppgiftsincident, leverantorsdelaktighet och om cybersakerhetslagen/sektorsregler galler.
5. Om rapporteringspliktig: anvand forutbestamd officiell rapporteringskanal och hall 24/72-tidslinjen; registrera ansvarig, inlamning och kvittens.
6. Aterstall fran verifierad ren kopia, validera redovisningsintegritet, meddela berorda parter enligt radgivning och gor efteranalys.

## Leverantorsbedomning

Prioritera hostingleverantor/databas, e-post/SMTP, betalning/Stripe, identitet, backup/lagring, AI/API och drift-/supportkonton. Bedom proportionerligt efter data och kritikalitet. Kontrollera avtal, personuppgiftsbitradesavtal dar behovligt, underleverantorer, dataplats/overforing, MFA och adminatkomst, incidentmeddelande, loggar, backup/restore, tillganglighet, revision/certifiering, dataexport, radering och exit. Dokumentera kvarstaende risk och godkannande; anta inte att en leverantors marknadsforing eller certifikat automatiskt uppfyller AliBooks krav.

## Uppfoljning och godkannandekriterier

- NIS2-scope har daterad, underbyggd slutsats och omprovningsdatum.
- VD/styrelse har fattat och signerat faktiskt beslut; ansvarig och resurser ar utsedda.
- MFA ar implementerad och verifierad for agarkonto och administrativa vagar.
- Tillgangs-/riskregister har agare, klassning, hot, atgard och accepterad slutrisk.
- Restore-test visar RPO/RTO, kontrollsummor och att appen kan lasa aterstallda data.
- Incidentovning visar eskalering, bevisbevarande och rapporteringsbeslut inom tidskrav.
- Kritiska leverantorer har dokumenterad bedomning, avtal och exit-/kontinuitetsplan.
- Ingen punkt far markeras klar enbart fran en plan, lokal statusruta eller gront CI.

## Ratt kalla

- [Cybersakerhetslag (2025:1506), aktuell svensk forfattning](https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/cybersakerhetslag-20251506_sfs-2025-1506/)
- [Cybersakerhetsforordning (2025:1507), tillsyn och ansvar](https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/cybersakerhetsforordning-20251507_sfs-2025-1507/)
- [Regeringen: Cybersakerhetslagen tradde i kraft 15 januari 2026](https://regeringen.se/pressmeddelanden/2026/01/nu-skarps-kraven-pa-svensk-cybersakerhet/)

Regler, myndighetsnamn och vagledning kan andras. Verifiera alltid gallande lydelse och aktuell behorig myndighet fore ett ratt eller rapporteringsbeslut.
