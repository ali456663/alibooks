# AWS Deployment Plan

This plan follows the assignment requirements: public URL, EC2 and AWS RDS.

## Target Architecture

```text
User browser
  -> HTTPS public URL
  -> EC2 instance
  -> Docker containers
      -> React frontend
      -> Spring Boot backend
  -> AWS RDS PostgreSQL
```

Optional later:

```text
Invoice files -> AWS S3
```

## AWS Resources

Create these resources:

- EC2 instance for running Docker containers
- RDS PostgreSQL database
- Security group for EC2
- Security group for RDS
- Optional S3 bucket for receipts/invoices

For the practical click-by-click checklist, see [aws-rds-ec2-checklista.md](aws-rds-ec2-checklista.md).

## EC2

Recommended simple setup:

```text
Instance type: t3.micro or t3.small
OS: Ubuntu Server
Ports open:
  22   SSH
  80   HTTP
  443  HTTPS
```

For a cleaner production setup, expose only `80` and `443` publicly and use Nginx as a reverse proxy.

## RDS PostgreSQL

Recommended setup:

```text
Engine: PostgreSQL
Database name: cloudshop
Username: cloudshop
Password: strong production password
Public access: No, if EC2 and RDS are in the same VPC
```

RDS should allow inbound PostgreSQL traffic from the EC2 security group:

```text
Port: 5432
Source: EC2 security group
```

Do not open RDS to everyone on the internet.

## Production Environment Variables

Backend needs:

```text
SERVER_PORT=3000
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/cloudshop
SPRING_DATASOURCE_USERNAME=cloudshop
SPRING_DATASOURCE_PASSWORD=<production-password>
JWT_SECRET=<strong-secret>
```

Frontend needs:

```text
VITE_API_URL=https://<your-public-api-url>
```

## Dockerhub Images

The GitHub Actions Dockerhub workflow should push:

```text
<dockerhub-username>/cloudshop-backend:latest
<dockerhub-username>/cloudshop-frontend:latest
```

On EC2, production can pull and run these images.

## Production Compose File

This repo includes:

```text
docker-compose.prod.yml
.env.production.example
frontend/Dockerfile.prod
frontend/nginx.conf
docs/dockerhub-release.md
```

On EC2:

```bash
cp .env.production.example .env
nano .env
FRONTEND_URL=http://<ec2-public-ip> BACKEND_URL=http://<ec2-public-ip>/api sh ./scripts/ec2-deploy.sh
```

The production compose file does not include a local `db` service because the database is AWS RDS.
The production frontend runs through Nginx on port `80`.
The backend runs on port `3000` inside Docker and is reached publicly through `/api`.

For a first demo without a domain, these values can use the EC2 public IP:

```text
VITE_API_URL=/api
APP_FRONTEND_URL=http://<ec2-public-ip>
```

Later, when you add HTTPS/domain, change them to:

```text
VITE_API_URL=/api
APP_FRONTEND_URL=https://your-domain.com
```

## HTTPS

Use HTTPS for public URLs.

Simple options:

- Use Nginx on EC2 with Let's Encrypt certificates
- Use an AWS Load Balancer with HTTPS certificate from AWS Certificate Manager

For the presentation, explain:

```text
Locally we use HTTP on localhost. In production, users access the app through HTTPS.
HTTPS encrypts login data, JWT tokens and order information.
```

## Deployment Steps

1. Push code to GitHub.
2. Run GitHub Actions CI.
3. Run Dockerhub workflow.
4. Create RDS PostgreSQL.
5. Create EC2 instance.
6. Install Docker on EC2.
7. Copy `.env.production.example` to `.env` on EC2.
8. Fill in Dockerhub username, image tag, RDS URL, JWT secret, Stripe and SMTP values.
9. Run `scripts/ec2-deploy.sh` to pull Docker images and start production containers.
10. Check container status and backend logs from the deploy script output.
11. Configure HTTPS/public URL.
12. Run the production smoke test.
13. Test register, login, product list and create order.

Smoke test from EC2/Linux:

```bash
FRONTEND_URL=http://<ec2-public-ip> BACKEND_URL=http://<ec2-public-ip>/api sh ./scripts/prod-smoke-test.sh
```

Smoke test from Windows PowerShell:

```powershell
.\scripts\prod-smoke-test.ps1 -FrontendUrl http://<ec2-public-ip> -BackendUrl http://<ec2-public-ip>/api
```

## What To Show In The Presentation

Show:

- Public frontend URL
- Public backend health endpoint
- EC2 instance in AWS console
- RDS database in AWS console
- Dockerhub images
- GitHub Actions pipeline
- Environment variables without showing secrets

Say:

```text
In the local environment, PostgreSQL runs in Docker Compose.
In production, the backend runs on EC2 and connects to AWS RDS PostgreSQL.
The application uses environment variables, so the same backend image can run locally and in the cloud.
```
