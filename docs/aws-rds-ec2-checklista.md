# AWS RDS + EC2 Checklista

Den har checklistan ar for den forsta publika AliBooks-demon.

Målet:

```text
Browser -> EC2 public IP -> Nginx frontend -> /api -> backend -> RDS PostgreSQL
```

## 1. Region

Anvand samma region for EC2 och RDS.

Rekommenderat:

```text
eu-north-1 Stockholm
```

## 2. Security Groups

Skapa två security groups.

### EC2 Security Group

Namn:

```text
alibooks-ec2-sg
```

Inbound rules:

```text
SSH   22   Your IP
HTTP  80   0.0.0.0/0
HTTPS 443  0.0.0.0/0
```

Viktigt:

```text
Oppna inte port 3000 publikt.
Backend nas via /api genom Nginx.
```

### RDS Security Group

Namn:

```text
alibooks-rds-sg
```

Inbound rules:

```text
PostgreSQL 5432  Source: alibooks-ec2-sg
```

Viktigt:

```text
RDS ska inte vara oppen for 0.0.0.0/0.
Databasen ska bara kunna nas fran EC2.
```

## 3. RDS PostgreSQL

Skapa RDS med:

```text
Engine: PostgreSQL
Template: Free tier eller Dev/Test
DB instance identifier: alibooks-db
Master username: cloudshop
Database name: cloudshop
Public access: No
Security group: alibooks-rds-sg
```

Spara:

```text
RDS endpoint
Database password
```

Endpointen ser ungefar ut sa har:

```text
alibooks-db.xxxxxx.eu-north-1.rds.amazonaws.com
```

## 4. EC2

Skapa EC2 med:

```text
AMI: Ubuntu Server
Instance type: t3.micro eller t3.small
Security group: alibooks-ec2-sg
Public IP: Enabled
```

Installera Docker pa EC2:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin git
sudo usermod -aG docker ubuntu
```

Logga ut och in igen efter `usermod`, eller kor:

```bash
newgrp docker
```

## 5. Projekt Pa EC2

Klona projektet:

```bash
git clone <your-github-repo-url>
cd <repo-folder>
```

Skapa `.env`:

```bash
cp .env.production.example .env
nano .env
```

Fyll i:

```text
DOCKERHUB_USERNAME=<your-dockerhub-user>
IMAGE_TAG=latest

VITE_API_URL=/api
APP_FRONTEND_URL=https://<your-domain>
TLS_CERT_HOST_PATH=/etc/letsencrypt/live/<your-domain>/fullchain.pem
TLS_KEY_HOST_PATH=/etc/letsencrypt/live/<your-domain>/privkey.pem

RDS_CA_CERT_HOST_PATH=/opt/alibooks/certs/global-bundle.pem
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/cloudshop?sslmode=verify-full&sslrootcert=/run/certs/rds-ca-bundle.pem
SPRING_DATASOURCE_USERNAME=alibooks_app
SPRING_DATASOURCE_PASSWORD=<rds-password>

JWT_SECRET=<long-random-secret-at-least-32-chars>
```

Place the current AWS RDS global CA bundle at the host path in `RDS_CA_CERT_HOST_PATH` before deployment. Compose mounts it read-only for pgJDBC; `verify-full` checks both the certificate chain and endpoint hostname. The JDBC driver's `require` mode encrypts traffic but accepts unverified server certificates. See [AWS RDS certificate bundles](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html) and [pgJDBC SSL settings](https://jdbc.postgresql.org/documentation/ssl/).

Issue a valid certificate for the public domain and set `TLS_CERT_HOST_PATH` to its full chain and `TLS_KEY_HOST_PATH` to its private key on EC2. Compose mounts both read-only into Nginx, which redirects port 80 to HTTPS on 443. Keep certificate renewal configured and reload Nginx after renewal. Do not send real data over the EC2 IP or HTTP; an external HTTPS load balancer is an alternative only if it is actually configured and verified.

Stripe och e-post kan vara testvarden i forsta molndemon:

```text
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
```

## 6. Deploy

Starta appen:

Satt `ALIBOOKS_AUTH_TOKEN` i skalets miljo utan att skriva vardet i kommandohistorik.
Deploy kraver token for att prova skyddad databasstatus. Vid forsta
agarkontots bootstrap, se undantaget i [Dockerhub-guiden](dockerhub-release.md);
bootstrap ar inte ett go-live-bevis.

```bash
FRONTEND_URL=https://<your-domain> BACKEND_URL=https://<your-domain>/api sh ./scripts/ec2-deploy.sh
```

Kontrollera manuellt:

```text
https://<your-domain>
https://<your-domain>/api/health
https://<your-domain>/api/system/status
```

## 7. Demo: Vad Du Ska Visa

Visa i AWS:

```text
EC2 instance running
EC2 security group with 80/443 and SSH
RDS instance running
RDS security group allowing 5432 only from EC2 security group
```

Visa i appen:

```text
Public frontend URL
Public API health URL
Settings/Systemstatus visar database ok
Registrering, login, kund, faktura, PDF, betalning, bokforing, momsrapport
```

Säg:

```text
Frontend och backend kor i Docker pa EC2.
Backend ansluter till PostgreSQL i AWS RDS.
Backend ar inte direkt oppen pa port 3000 publikt, utan nas via /api genom Nginx.
Hemligheter laggs i environment variables och visas inte i kod.
```
