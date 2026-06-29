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

The frontend starts on `http://localhost:5173`.

## Demo Flow

1. Start the Spring Boot backend in IntelliJ.
2. Start the React frontend with `npm run dev`.
3. Open `http://localhost:5173`.
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
APP_FRONTEND_URL=http://localhost:5173
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

## Docker

Start the full local environment:

```bash
docker compose up --build
```

This starts:

- React frontend on `http://localhost:5173`
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

## CI/CD

GitHub Actions has two workflows:

- `CI`: builds the frontend, tests the backend, and builds Docker images.
- `Dockerhub`: builds and pushes Docker images to Dockerhub.

The CI backend job starts a PostgreSQL service container so Spring Boot context tests can connect to a real database during the pipeline.
The CI workflow can also be started manually from GitHub Actions with `Run workflow`.

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
