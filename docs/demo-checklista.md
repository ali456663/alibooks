# Demo-checklista för CloudShop

Fokusera på att visa helheten. Gå inte igenom all kod rad för rad.

## 1. Demo av appen

Visa den publika URL:en.

Säg:

```text
Här är vår publika frontend. Vi visar inte localhost i den riktiga demon, utan den deployade versionen.
```

Visa:

- registrering
- inloggning
- produktlista
- skapa beställning
- ordern visas i orderlistan

Kort manus:

```text
Först registrerar jag en ny användare. När användaren är inloggad får frontend en JWT-token.
Sedan hämtar vi produkter från backend och skapar en beställning.
Beställningen sparas i databasen och visas i orderlistan.
```

## 2. Molndelen

Visa:

- publik frontend-länk
- publik backend/API-länk
- EC2-instans där appen körs
- RDS PostgreSQL-databas
- `docs/go-live-checklista.md`

Säg:

```text
Backend körs i molnet och ansluter till PostgreSQL i AWS RDS.
Lokalt använder vi Docker Compose med PostgreSQL, men i produktion pekar samma backend mot RDS genom environment variables.
```

## 3. Docker och lokal miljö

Visa:

- `frontend/Dockerfile`
- `backend/Dockerfile`
- `docker-compose.yml`

Säg:

```text
Docker Compose startar frontend, backend och PostgreSQL tillsammans.
Det gör att den lokala miljön liknar produktion: backend kör som container och ansluter till PostgreSQL med environment variables.
Skillnaden är att produktion använder AWS RDS istället för den lokala db-containern.
```

Kommando:

```bash
docker compose up --build
```

## 4. CI/CD

Visa först GitHub Actions:

- `.github/workflows/ci.yml`
- pipeline-run i GitHub

Säg:

```text
CI-pipelinen bygger frontend, kör backend-tester och bygger Docker images.
```

Visa sedan Dockerhub workflow:

- `.github/workflows/dockerhub.yml`
- Dockerhub images
- `docs/dockerhub-release.md`

Säg:

```text
Den här pipelinen bygger och pushar Docker images till Dockerhub.
Den publicerar latest, sha-versioner och release-taggar som v1.0.0.
EC2 kan sedan välja version med IMAGE_TAG i .env.
```

Om ni använder AWS CodePipeline:

```text
Efter GitHub Actions kan vi visa AWS CodePipeline, som hämtar den färdiga imagen och deployar den i AWS.
```

## 5. Tester

Visa:

- `CloudShopApplicationTests`
- `UserServiceTest`
- `JwtServiceTest`
- `OrderControllerTest`
- `ProductServiceTest`

Säg:

```text
Vi testar kärnfunktionerna: att appen startar, registreringsvalidering, login,
JWT-token och att order kräver JWT.
```

## 6. VG: Mikrotjänster, HTTPS och JWT

Visa dokumentet:

- `docs/microservices-jwt-https.md`

Säg:

```text
För VG beskriver vi appen som uppdelad i auth-service, product-service,
order-service och invoice-service. Frontend kommunicerar med tjänsterna via REST API:er.
Lokalt kan det vara HTTP, men i produktion använder vi HTTPS så att JWT och känslig data skickas krypterat.
```

JWT-manus:

```text
När användaren loggar in skapar auth-service en JWT-token.
Frontend skickar token i Authorization-headern när den skapar en order.
Order-service verifierar token innan ordern sparas.
```

Header:

```text
Authorization: Bearer <jwt-token>
```

## Rekommenderad demoordning

1. Publik frontend
2. Registrera användare
3. Logga in
4. Visa produkter
5. Skapa beställning
6. Visa orderlistan
7. Visa backend/API
8. Visa EC2
9. Visa RDS
10. Visa Dockerfile och Docker Compose
11. Visa GitHub Actions
12. Visa Dockerhub
13. Visa tester
14. Visa mikrotjänster/JWT/HTTPS för VG

## Viktig formulering

```text
Vi fokuserar på helheten: att appen fungerar, hur den körs i molnet,
hur lokal miljö och produktion liknar varandra, hur CI/CD bygger och testar,
och hur JWT används för säker kommunikation mellan frontend och backend.
```
