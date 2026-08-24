# AliBooks anvandningsklar MVP - 20 steg

Den har checklistan ar ett praktiskt kontrakt for nar AliBooks kan borja anvandas som lokal MVP. Den ersatter inte extern produktionsverifiering, redovisningskonsult eller juridisk kontroll, men den samlar vad som maste vara sant innan riktig kund- och bokforingsdata flyttas in.

Statusnivaer:

- `KLAR LOKALT`: AliBooks har lokal funktion och automatisk kontroll.
- `MANUELLT BEVIS`: funktionen finns, men ska klicktestas eller granskas innan riktig anvandning.
- `EXTERN BLOCKER`: kraver extern tjanst, publik miljo, avtal eller myndighetskontroll.

| Nr | Omrade | MVP-krav | Bevis |
| --- | --- | --- | --- |
| 01 | Lokal start | Docker/PostgreSQL, backend och frontend kan startas utan vit sida. | `npm run doctor`, `npm run smoke:runtime`, `npm run check:views` |
| 02 | Inloggning | Registrering, login, JWT och skyddade API-anrop fungerar. | `npm run check:api-contract`, backendtester |
| 03 | Kundregister | Kund kan skapas, valideras, sokas och arkiveras utan att historik tappas. | `npm run check:acceptance`, `npm run check:data-safety` |
| 04 | Fakturering | Faktura kan skapas, skickas, exporteras som PDF och foljas upp. | `npm run check:acceptance`, manuellt PDF-bevis |
| 05 | Betalning | Betalning och delbetalning kan registreras, historik visas och forfallna fakturor foljs. | `npm run check:api-contract`, manuellt betalningsbevis |
| 06 | Stripe | Testnycklar, webhook och fallback for hemsideforsaljning ar separerade fran skarp drift. | `npm run check:prod`, `npm run check:manual-go-live` |
| 07 | Bokforing | Verifikat balanserar debet/kredit och destruktiva andringar skyddas. | `npm run check:data-safety`, backendtester |
| 08 | Autokontering | Bank-CSV och regler kan foresla bokforing utan att lova riktig bankkoppling. | `npm run check:speedledger-parity` |
| 09 | Bankavstamning | Bankrader kan importeras, matchas, hoppas over och exporteras. | `npm run check:api-contract`, `npm run check:data-safety` |
| 10 | Underlag | Kvitton, PDF:er och fakturaunderlag kan sparas och exporteras. | `npm run check:manual-go-live`, `npm run check:backup` |
| 11 | Moms | Momsrapport, momsavstamning och preliminar skatt visar arbetsunderlag. | `npm run check:acceptance`, manuellt Skatteverket-bevis |
| 12 | Rapporter | Resultatrapport, balansrapport, forsjaljningsrapport och sokbara underlag finns. | `npm run check:speedledger-parity`, `npm run check:ready` |
| 13 | Lon MVP | Loneunderlag och arbetsgivardeklarationsunderlag finns som arbetsmaterial. | `npm run check:speedledger-parity` |
| 14 | Bokslut | Periodlasning, arsbokslut, NE/INK2/K2-arbetsunderlag och redovisningspaket finns. | `npm run check:professional-loop`, `npm run check:speedledger-parity` |
| 15 | Backup | Backup kan skapas och katalogverifieras. | `npm run check:backup` |
| 16 | Restore | Restore drill till separat testdatabas ar dokumenterad och kravs fore skarp data. | `npm run check:manual-go-live`, restore-script |
| 17 | Sakerhet | Secrets, JWT, CORS, AI-sakert lage och personuppgifter ar kontrollerade. | `npm run check:secrets`, `npm run check:prod`, Sakerhet-vyn |
| 18 | CI/CD | GitHub Actions, Dockerhub workflow och release-gate finns och ar sparbara. | `npm run check:ci`, `npm run check:release-traceability` |
| 19 | Git och release | Lokal kod ar commitad, syncad innan GitHub-bevis och image-taggar ar sparbara. | `npm run check:git`, `npm run check:sync`, `npm run check:prepush -- --allow-ahead` |
| 20 | Go-live beslut | Startklar skiljer lokal MVP fran skarp produktion och blockerar skarp kunddata tills externa bevis finns. | `npm run check:startklar`, `npm run check:go-live-risks`, `npm run check:use-today` |

## Beslut

AliBooks ar anvandningsklar som lokal MVP nar alla 20 steg ovan ar grona och manuella bevis for PDF, e-post, Stripe, bank/betalningsflode, underlag, backup och restore drill ar sparade.

Innan bredare anvandning ska en begransad pilotdrift kontrolleras med `npm run check:pilot` och [pilotdrift-mvp.md](pilotdrift-mvp.md). Pilotdrift betyder max 1-3 riktiga kunder forst, daglig backup, daglig avstamning och stopp om fakturanummer, verifikat, moms, backup eller persondata verkar fel.

AliBooks ar inte skarp produktionsklar for riktig kunddata innan:

- GitHub Actions ar gron efter push.
- Dockerhub-images ar publicerade med ratt `IMAGE_TAG`.
- EC2/RDS ar verifierade med produktions-smoke.
- Backup och restore drill ar testade utanfor produktion.
- Stripe och SMTP ar testade med riktiga testfloden.
- Extern bankkoppling, PEPPOL/e-faktura, NE-inlamning, arsredovisning, E-dagsavslut, factoring och fullservice hanteras via extern leverantor eller konsult.
