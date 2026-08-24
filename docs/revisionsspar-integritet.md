# AliBooks revisionsspar och integritet

Detta dokument beskriver den minsta professionella kontrollen for AliBooks revisionsspar.
Syftet ar att kunna visa vad som har hant i systemet och att viktiga export- och backupfloden har kontrollvarden.

## Princip

AliBooks ska spara ett revisionsspar for viktiga handelser:

- registrering och inloggning
- skapande och andring av kunder, tjanster, fakturor och leverantorer
- betalningar, delbetalningar och aterbetalningar
- bokforing, moms, SIE-export och rapportexport
- underlag, kvittohashar och backup
- drift- och sakerhetsnara handelser

Revisionssparet ska inte anvandas for att dolja fel. Fel ska rattas med nya handelser, rattelseverifikat eller tydlig statusandring.

## Integritetskedja

Backend kan skapa en integritetsrapport for revisionssparet:

- varje audit-rad far en radkod med `SHA-256`
- varje rad binds till foregaende rad med en kedjekod
- forsta raden startar fran `START`
- hela exporten far en auditstampel/fingerprint

Om en tidigare rad andras ska auditstampeln andras. Det ar inte samma sak som juridiskt oforanderlig lagring, men det ger en stark lokal kontroll for MVP, backup, restore och overlamning till redovisningskonsult.

## Export

AliBooks ska kunna exportera:

- `revisionsspar.csv`
- `revisionsspar-integritet.csv`
- backup med `integrityProofs.auditTrail`
- backup med `integrityManifest` och `SHA-256`

Vid bokslut, restore-test eller molnflytt ska du spara auditstampel och slutlig kedjekod tillsammans med bokforingsexporter.

## Stoppsignaler

Stoppa export, bokslut eller produktionsflytt om:

- revisionssparet inte gar att ladda
- auditstampel saknas i backup
- slutlig kedjekod saknas
- antal audit-handelser i backup inte matchar integritetsrapporten
- release-gaten inte kor `check:audit-integrity`
- backendtester for auditkedjan failar

## Kommando

```bash
npm run check:audit-integrity
```

Kommando ska ingå i release-gaten och i MVP-evidensen.
