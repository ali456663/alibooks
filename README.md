# AliBooks

AliBooks is a small cloud-based invoicing and business dashboard app.

The app will show:

- User registration and login
- Service listing
- Invoice creation
- Invoice number and invoice date
- Approved for F-tax text
- VAT calculation
- Stripe Checkout test integration
- Invoice PDF generation
- Docker-based local environment
- CI/CD pipeline
- Cloud deployment with public URLs

## Project Structure

```text
alibooks/
  frontend/
  backend/
  docker-compose.yml
```

## Planned Stack

Frontend:

- React
- React Router
- Tailwind CSS
- React Hook Form
- Zod

Backend:

- Java
- Spring Boot
- PostgreSQL
- JWT authentication

## Local Development

For a step-by-step local startup and MVP test flow, see [docs/kom-igang-snabbt.md](docs/kom-igang-snabbt.md).
For manual MVP testing, see [docs/mvp-testprotokoll.md](docs/mvp-testprotokoll.md).
For the remaining big steps and priorities, see [docs/roadmap-kvar.md](docs/roadmap-kvar.md).
For the first cloud go-live flow, see [docs/go-live-checklista.md](docs/go-live-checklista.md).
For the professional 20-step accounting control loop, see [docs/professionell-bokforing-loop.md](docs/professionell-bokforing-loop.md).

Backend in IntelliJ:

1. Open the `backend` folder as a Maven project.
2. Run `CloudShopApplication`.
3. The backend starts on `http://localhost:3000`.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:5157`.

After larger changes, run the local release gate:

```bash
npm run check:release
```

Before push or release, include backend tests:

```bash
npm run check:release -- --with-backend
```

Before deploy, include local Docker image builds too:

```bash
npm run check:release -- --with-backend --with-docker-build
```

The release gate runs the main static checks, frontend build and runtime smoke test in the same order every time.
The `--with-backend` flag runs backend tests through Maven or Docker.
The `--with-docker-build` flag also builds backend and frontend Docker images locally.

Individual checks:

```bash
npm run build
npm run check:api-contract
npm run check:ready
npm run check:backend-wiring
npm run check:docs
npm run check:docker
npm run check:prod
npm run check:secrets
npm run check:views
npm run smoke:runtime
npm run test:backend
```

The runtime smoke test opens AliBooks in Chrome and fails if the app renders the root crash fallback.
The readiness check verifies the practical go-live foundation: CI, Docker, env templates, docs, professional 20-step loop, Stripe/SMTP/JWT configuration points and key backend/frontend capabilities.
The backend wiring check catches constructor and request-record mismatches that would otherwise show up as Java compilation errors in GitHub Actions.
The Docker check makes sure the local and production Docker setup stays aligned with the app ports, Node version, backend port and `/api` proxy.
The production readiness check verifies the EC2/RDS `.env` shape, same-origin `/api`, CORS, disabled test reset flags, JWT settings, production compose and smoke scripts.
The secrets check fails if real-looking Stripe, AI, AWS or private key material is accidentally committed.
The view check makes sure every left-menu view has a rendered screen, which reduces the risk of a blank page after navigation.
The backend test command uses Maven if available, or Docker with a Maven Java 21 image if Maven is not installed locally.

## Demo Flow

1. Start the Spring Boot backend in IntelliJ.
2. Start the React frontend with `npm run dev`.
3. Open `http://localhost:5157`.
4. Register a user.
5. Log in.
6. View products.
7. Create an order.

The authentication flow uses JWT and BCrypt password hashing. Users, services and invoices are stored in PostgreSQL.

## Local Database

Start PostgreSQL with Docker:

```bash
docker compose up db
```

The local database uses:

```text
Database: cloudshop
Username: cloudshop
Password: cloudshop
Port: 5432
```

In production, the same Spring Boot app can use AWS RDS PostgreSQL by changing the datasource environment variables.

## Environment Variables

Use `.env.example` as the safe template for local secrets and configuration.
Use `.env.production.example` as the safe template for EC2/RDS production configuration.

For Docker Compose:

```bash
cp .env.example .env
```

Then edit `.env` and replace the example Stripe, SMTP and JWT values. The real `.env` file is ignored by Git.
Real environment files such as `.env`, `.env.local`, `.env.production` and `.env.staging` are ignored by Git. Only safe templates like `.env.example` and `.env.production.example` should be committed.
Use a long unique `JWT_SECRET` for login security, preferably at least 32 characters.

For IntelliJ:

1. Open `Run > Edit Configurations`.
2. Choose `CloudShopApplication`.
3. Paste the needed values into `Environment variables`.
4. Restart the backend.

Important variables:

```text
JWT_SECRET
STRIPE_SECRET_KEY
STRIPE_WEBHOOK_SECRET
SPRING_MAIL_HOST
SPRING_MAIL_USERNAME
SPRING_MAIL_PASSWORD
APP_FRONTEND_URL
APP_INVOICE_REMINDERS_CRON
APP_TIME_ZONE
```

`APP_INVOICE_REMINDERS_CRON` controls the automatic invoice reminder schedule. The default is `0 0 9 * * *`, which means every day at 09:00 in `APP_TIME_ZONE`.

## Stripe Checkout

Set a Stripe test secret key before starting the backend:

```bash
STRIPE_SECRET_KEY=sk_test_...
APP_FRONTEND_URL=http://localhost:5157
```

Then create an invoice and click `Stripe` in the invoice list.

The app creates a Stripe Checkout Session on the backend and redirects the browser to Stripe's hosted checkout page.

For webhook-based payment confirmation, also set:

```bash
STRIPE_WEBHOOK_SECRET=whsec_...
```

The webhook endpoint is:

```text
POST /stripe/webhook
```

When Stripe sends `checkout.session.completed` with `metadata.invoiceId`, the backend marks the AliBooks invoice as paid and creates payment bookkeeping entries.

For sales that start on an external website, for example `musclefocusfitness.com`, Stripe can send the same webhook without `metadata.invoiceId`.
AliBooks then treats it as a website sale and books:

```text
1580 Fordran hos Stripe          debit
3041 Forsaljning tjanster 25 %   credit
2611 Utgaende moms               credit
```

Stripe may retry webhooks, so processed Stripe event IDs are stored in `stripe_webhook_events` to avoid double bookkeeping.

If the external website webhook is not connected yet, use the payment view to book a manual website Stripe sale with date, amount including VAT and Stripe reference.
AliBooks uses the same accounts as the webhook flow and blocks reused non-empty website sale references.

When Stripe later pays out money to the bank account, use the payment view to book the payout manually:

```text
1930 Foretagskonto               debit, net payout
6570 Bankkostnader               debit, Stripe fee
1580 Fordran hos Stripe          credit, gross payout
```

Booked Stripe payouts are stored in `stripe_payouts` with payout date, gross amount, fee, net amount, reference and voucher number.
If a Stripe payout reference is reused, AliBooks blocks it to reduce the risk of double bookkeeping.

## AI Assistant

The AI assistant is called through the Spring Boot backend, so AI API keys are never exposed in React.
AliBooks can use an OpenAI-compatible provider such as FreeLLMAPI, then Gemini, then Hugging Face. If no external AI key works, it falls back to a local rule-based assistant.
Before external AI calls, AliBooks minimizes the context and masks direct identifiers such as emails, personnummer, phone numbers and addresses.

Optional environment variables:

```text
GEMINI_API_KEY=AIza...
GEMINI_MODEL=gemini-3.5-flash
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta
HF_TOKEN=hf_...
HF_MODEL=moonshotai/Kimi-K2-Instruct-0905
HF_BASE_URL=https://router.huggingface.co/v1
AI_OPENAI_API_KEY=your-router-key
AI_OPENAI_MODEL=moonshotai/Kimi-K2-Instruct-0905
AI_OPENAI_BASE_URL=http://localhost:8000/v1
AI_OPENAI_PROVIDER_NAME=freellmapi
```

If external AI is unavailable, AliBooks falls back to a local rule-based assistant for invoices, bookkeeping, VAT, receipts, payments, reports and settings.

## Docker

Start the full local environment:

```bash
docker compose up --build
```

This starts:

- React frontend on `http://localhost:5157`
- Spring Boot backend on `http://localhost:3000`
- PostgreSQL database on `localhost:5432`

The local Docker environment is similar to production because the backend runs as a container and connects to PostgreSQL through environment variables. In production, the database URL points to AWS RDS instead of the local `db` container.

For EC2/RDS production, use:

```bash
cp .env.production.example .env
docker compose -f docker-compose.prod.yml up -d
```

Or use the EC2 deploy helper:

```bash
FRONTEND_URL=http://your-ec2-public-ip BACKEND_URL=http://your-ec2-public-ip/api sh ./scripts/ec2-deploy.sh
```

The production compose file does not start PostgreSQL locally. It expects `SPRING_DATASOURCE_URL` to point to AWS RDS.
The production frontend image is built with `frontend/Dockerfile.prod` and served by Nginx on port `80`.
`VITE_API_URL` is written into `/config.js` when the container starts, so the same frontend image can point to different backend URLs.

After deployment, run a smoke test:

```bash
FRONTEND_URL=http://your-ec2-public-ip BACKEND_URL=http://your-ec2-public-ip/api sh ./scripts/prod-smoke-test.sh
```

From Windows PowerShell:

```powershell
.\scripts\prod-smoke-test.ps1 -FrontendUrl http://your-ec2-public-ip -BackendUrl http://your-ec2-public-ip/api
```

Before starting production containers on EC2, validate the real `.env` without printing secrets:

```bash
cd frontend
npm run check:prod -- --env-file ../.env --strict
```

## CI/CD

GitHub Actions has two workflows:

- `CI`: runs the frontend release gate, tests the backend, and builds Docker images.
- `Dockerhub`: builds and pushes Docker images to Dockerhub.

The CI backend job starts a PostgreSQL service container so Spring Boot context tests can connect to a real database during the pipeline.
The CI frontend job runs `npm run check:release`, so GitHub Actions uses the same frontend release gate that you can run locally before push.
The CI Docker job builds the frontend with `frontend/Dockerfile.prod`, the same production Dockerfile used by the Dockerhub release workflow.
Both workflows use read-only repository permissions and concurrency groups, so repeated pushes do not leave stale CI runs for the same branch.
The CI workflow can also be started manually from GitHub Actions with `Run workflow`.

## Audit trail and protected history

AliBooks records important actions in the backend audit trail, including invoices, payments, corrections, period locks and exports.
Period close-check, journal entries, general ledger, profit/loss, balance, trial balance, VAT control, voucher-control, journal-integrity, account sign-control, bank reconciliation, receivables, payables, invoice, customer, supplier-invoice, VAT-filing and payroll exports are also logged with blocker, warning, late-voucher, fingerprint, entry, result, balance-difference, row and issue counts, so exported closing evidence and sensitive data exports are traceable.
Customer create/update/archive/restore/delete actions and bank reconciliation create/clear/remove-skipped actions are also logged, with messages that avoid personal numbers, addresses and phone numbers.
Service create/update actions are logged with effective invoice price, so price list changes are traceable before invoices, reports and closing evidence are reviewed.
The app can export both the full backend audit log (`/audit-events/export`) and an integrity chain (`/audit-events/integrity/export`) with hashes that can be saved with closing evidence or accountant handoff.
The accountant package includes voucher approval counts for approved, missing, pending and blocked approvals, so an accountant can see whether period vouchers have been reviewed before handoff.
The yearly archive control includes the same voucher approval counts and warns before final SIE/CSV handoff if annual vouchers are missing approval, pending approval or blocked.

## Professional bookkeeping controls

AliBooks has a backend period-lock control at `/accounting-period/close-check`.
It checks balance report, trial balance, voucher balance, VAT control, VAT filing evidence, bank reconciliation, receivables, payables, voucher approvals, account sign control and journal integrity before a period can be locked.
Manual multi-line vouchers and opening-balance vouchers are validated before booking: every line must have one account, either debit or credit, no negative amounts, no debit and credit on the same line, and total debit must equal total credit.
Voucher control also flags malformed historic/imported journal rows with negative amounts, zero rows or debit and credit on the same row, so old data can be reviewed before closing or SIE export.
Invoice amount changes are guarded in the domain model: total must equal net plus VAT, and credit invoices must use consistent negative amounts instead of mixed signs.
Service prices are validated in the backend before they can be used for invoices: service name is required, ordinary price must be greater than 0, discount price cannot be negative, and an active discount must be lower than the ordinary price.
VAT control flags missing output VAT on sales and warns when purchases or expenses are booked without input VAT, so VAT-free, reverse-charge and non-deductible purchases can be reviewed before filing.
Voucher control also checks evidence traceability, including missing receipts, missing receipt hashes, unclear sources and invoice numbers.
It also flags reused voucher numbers when the same voucher number points to different dates or different source references.
Account sign control uses closing balances through the selected period end, so negative balance-sheet accounts from earlier months are still caught before closing.
It covers common balance-sheet risk accounts such as bank, customer receivables, fixed assets, accumulated depreciation, supplier debt, tax debt, payroll tax, employer contributions, Stripe receivables and VAT accounts.
The control also warns when vouchers appear to be booked more than 35 days after the voucher date, so late bookkeeping can be reviewed before month close, VAT reporting or accountant handoff.
That warning is also surfaced in Compliance, Accounting quality, Risk center and Go-live readiness so late bookkeeping affects the same professional decision views used before production use.

For Dockerhub publishing, add these GitHub repository secrets:

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
```

The Dockerhub workflow can be started manually with `workflow_dispatch`, or by pushing a tag like:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow publishes `latest`, `sha-<commit>` and version tags like `v1.0.0`.
See [docs/dockerhub-release.md](docs/dockerhub-release.md) for the release and EC2 image-tag flow.
For GitHub secrets, create `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` under repository Actions secrets.

## Tests

The backend includes tests for:

- Spring Boot application startup
- Product model behavior
- User registration validation
- User login
- JWT token creation and validation
- Invoice creation requiring JWT
- Stripe website sale bookkeeping
- Stripe payout bookkeeping

Run tests in IntelliJ or with:

```bash
cd backend
mvn test
```

## VG: Microservices, HTTPS and JWT

See [docs/microservices-jwt-https.md](docs/microservices-jwt-https.md).

## Presentation

See [docs/demo-checklista.md](docs/demo-checklista.md).

## AWS Deployment

See [docs/aws-deployment-plan.md](docs/aws-deployment-plan.md).
For the practical RDS and EC2 checklist, see [docs/aws-rds-ec2-checklista.md](docs/aws-rds-ec2-checklista.md).
For the full first deploy runbook, see [docs/go-live-checklista.md](docs/go-live-checklista.md).

Cloud:

- EC2 for services
- AWS RDS for PostgreSQL
- S3 for receipts/invoices
- Dockerhub for Docker images
- GitHub Actions for CI/CD
