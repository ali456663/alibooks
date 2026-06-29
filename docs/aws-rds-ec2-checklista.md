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
APP_FRONTEND_URL=http://<ec2-public-ip>

SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/cloudshop
SPRING_DATASOURCE_USERNAME=cloudshop
SPRING_DATASOURCE_PASSWORD=<rds-password>

JWT_SECRET=<long-random-secret-at-least-32-chars>
```

Stripe och e-post kan vara testvarden i forsta molndemon:

```text
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
```

## 6. Deploy

Starta appen:

```bash
FRONTEND_URL=http://<ec2-public-ip> BACKEND_URL=http://<ec2-public-ip>/api sh ./scripts/ec2-deploy.sh
```

Kontrollera manuellt:

```text
http://<ec2-public-ip>
http://<ec2-public-ip>/api/health
http://<ec2-public-ip>/api/system/status
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
