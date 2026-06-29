# Microservices, HTTP and JWT

CloudShop can be split into microservices for the VG part of the assignment.

## Services

```text
frontend-react
  -> auth-service
  -> product-service
  -> order-service
  -> invoice-service
```

## Responsibilities

Auth service:

- Registers users
- Logs users in
- Creates JWT tokens

Product service:

- Stores products
- Returns product lists and product details

Order service:

- Receives order requests
- Validates JWT tokens
- Stores orders in PostgreSQL

Invoice service:

- Creates receipt or invoice PDFs
- Stores files in S3

## Communication

The frontend calls the services with HTTP requests.

In production, the public traffic should use HTTPS:

```text
https://frontend.example.com
https://api.example.com/auth
https://api.example.com/products
https://api.example.com/orders
```

HTTPS means that HTTP traffic is encrypted with TLS. This protects login details, JWT tokens and order data while they travel over the internet.

## JWT Flow

```text
1. User registers or logs in through auth-service.
2. Auth service validates the credentials.
3. Auth service returns a JWT token.
4. Frontend stores the token temporarily.
5. Frontend sends the token in the Authorization header.
6. Product/order/invoice services validate the JWT before protected actions.
```

Example header:

```text
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Example Request Flow

Create order:

```text
React frontend
  -> HTTPS POST /orders
  -> Authorization: Bearer <jwt>
  -> order-service validates JWT
  -> order-service saves order in PostgreSQL/RDS
  -> order-service returns created order
```

## HTTP vs HTTPS

For the assignment, it is correct to say that services communicate through HTTP APIs.

For production, it is better to show the public app with HTTPS URLs. HTTPS is still HTTP, but with encryption:

```text
HTTP + TLS = HTTPS
```

Local development can use:

```text
http://localhost:5173
http://localhost:3000
```

Production should use:

```text
https://your-frontend-url
https://your-api-url
```

## Demo Explanation

Short explanation for the presentation:

```text
For VG, we describe the app as separate services: auth, product, order and invoice.
They expose REST APIs. The frontend calls these APIs over HTTPS in production.
When a user logs in, the auth service creates a JWT. The frontend sends that JWT
in the Authorization header when creating orders. The order service validates the
token before saving the order.
```

