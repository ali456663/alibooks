# Go-live Checklista

Den har checklistan ar for forsta riktiga molnkorningen av AliBooks.

Malet:

```text
GitHub Actions -> Dockerhub -> EC2 -> RDS -> publik app -> smoke test -> demo
```

## 0. Innan Du Borjar

Kontrollera lokalt:

```text
Frontend bygger med npm run build
Backend startar i IntelliJ
Docker Desktop fungerar
MVP-flodet fungerar lokalt
```

Dokument:

```text
docs/mvp-testprotokoll.md
docs/dockerhub-release.md
docs/aws-rds-ec2-checklista.md
```

## 1. GitHub Actions CI

I GitHub:

```text
Actions -> CI -> Run workflow
```

Klart nar:

```text
Backend build and test = green
Frontend build = green
Docker build = green
```

Om CI failar:

```text
Fixa CI innan du gar vidare till Dockerhub.
Annars riskerar du att pusha trasiga images.
```

## 2. Dockerhub Secrets

I GitHub:

```text
Settings -> Secrets and variables -> Actions
```

Kontrollera att dessa finns:

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
```

Viktigt:

```text
DOCKERHUB_TOKEN ska vara Dockerhub access token, inte vanligt losenord.
```

## 3. Dockerhub Workflow

I GitHub:

```text
Actions -> Dockerhub -> Run workflow
```

Klart nar workflowen ar green.

Kontrollera i Dockerhub:

```text
cloudshop-backend:latest
cloudshop-frontend:latest
cloudshop-backend:sha-...
cloudshop-frontend:sha-...
```

Vill du skapa en fast demo-version:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Da kan EC2 anvanda:

```text
IMAGE_TAG=v1.0.0
```

## 4. AWS RDS

Skapa eller kontrollera RDS:

```text
Engine: PostgreSQL
Database name: cloudshop
Username: cloudshop
Public access: No
Security group: alibooks-rds-sg
```

RDS security group:

```text
PostgreSQL 5432 from alibooks-ec2-sg
```

Spara:

```text
RDS endpoint
RDS password
```

## 5. AWS EC2

Skapa eller kontrollera EC2:

```text
Ubuntu Server
t3.micro eller t3.small
Security group: alibooks-ec2-sg
Public IP enabled
```

EC2 security group:

```text
SSH 22 from your IP
HTTP 80 from 0.0.0.0/0
HTTPS 443 from 0.0.0.0/0
```

Viktigt:

```text
Oppna inte backend-port 3000 publikt.
API gar via /api genom Nginx.
```

## 6. Installera Docker Pa EC2

Kor pa EC2:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin git
sudo usermod -aG docker ubuntu
newgrp docker
```

Kontrollera:

```bash
docker --version
docker compose version
```

## 7. Klona Projektet Pa EC2

Kor pa EC2:

```bash
git clone <your-github-repo-url>
cd <repo-folder>
```

## 8. Skapa Production .env Pa EC2

Kor:

```bash
cp .env.production.example .env
nano .env
```

Fyll i minst:

```text
DOCKERHUB_USERNAME=<your-dockerhub-user>
IMAGE_TAG=latest

VITE_API_URL=/api
APP_FRONTEND_URL=http://<ec2-public-ip>

SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/cloudshop
SPRING_DATASOURCE_USERNAME=cloudshop
SPRING_DATASOURCE_PASSWORD=<rds-password>

JWT_SECRET=<long-random-secret-at-least-32-chars>
```

Om du vill testa e-post och Stripe:

```text
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_USERNAME=<gmail>
SPRING_MAIL_PASSWORD=<google-app-password>
```

Visa aldrig `.env` med hemligheter i presentationen.

## 9. Deploy Pa EC2

Kor:

```bash
FRONTEND_URL=http://<ec2-public-ip> BACKEND_URL=http://<ec2-public-ip>/api sh ./scripts/ec2-deploy.sh
```

Klart nar:

```text
frontend container = running
backend container = running
backend logs visar att Spring Boot startade
smoke test passed
```

## 10. Kontrollera Publika Lankar

Oppna:

```text
http://<ec2-public-ip>
http://<ec2-public-ip>/api/health
http://<ec2-public-ip>/api/system/status
```

Forvantat:

```text
Frontend visas
/api/health visar status ok
/api/system/status visar database ok
```

## 11. Testa Demo-flodet Publikt

Testa pa den publika URL:en:

```text
Registrera konto
Logga in
Skapa kund
Skapa faktura
Generera PDF
Markera skickad
Registrera betalning
Kontrollera bokforing
Kontrollera momsrapport
Exportera CSV
```

## 12. Presentation

Visa i ordning:

```text
1. Publik app
2. Publik API health
3. GitHub Actions CI
4. Dockerhub workflow
5. Dockerhub image tags
6. EC2 instance
7. RDS database
8. EC2 security group
9. RDS security group
10. docker-compose.prod.yml
11. Smoke test output
12. Appens demo-flode
```

Kort manus:

```text
CI testar och bygger projektet.
Dockerhub workflow publicerar versionerade Docker images.
EC2 pullar images och kor frontend/backend i Docker.
Backend ansluter till PostgreSQL i AWS RDS.
Frontend och API ar publika via samma EC2-adress, dar API gar via /api genom Nginx.
Hemligheter ligger i environment variables och visas inte i kod.
```

## 13. Snabb Felsokning

Vit sida:

```text
Kontrollera browser console.
Kontrollera att frontend container ar running.
Kontrollera /config.js.
```

API fungerar inte:

```text
Oppna http://<ec2-public-ip>/api/health.
Kontrollera frontend/nginx.conf.
Kontrollera backend logs.
```

Databas fungerar inte:

```text
Kontrollera SPRING_DATASOURCE_URL i .env.
Kontrollera RDS security group.
Kontrollera att RDS och EC2 ar i samma VPC.
```

Dockerhub pull fungerar inte:

```text
Kontrollera DOCKERHUB_USERNAME.
Kontrollera IMAGE_TAG.
Kontrollera att tags finns i Dockerhub.
```
