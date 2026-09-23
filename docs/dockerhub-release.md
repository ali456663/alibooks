# Dockerhub Release Guide

This guide explains how to publish AliBooks Docker images for EC2.

## GitHub Secrets

Add these secrets in GitHub:

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
```

Use a Dockerhub access token, not your normal Dockerhub password.

Where to add them:

```text
GitHub repository
-> Settings
-> Secrets and variables
-> Actions
-> New repository secret
```

Create two secrets:

```text
Name: DOCKERHUB_USERNAME
Value: your Dockerhub username

Name: DOCKERHUB_TOKEN
Value: Dockerhub access token
```

Create the Dockerhub token here:

```text
Dockerhub
-> Account settings
-> Personal access tokens
-> Generate new token
```

Give it read/write permission so GitHub Actions can push images.

Do not put the Dockerhub token in `.env`, README, screenshots or code.

## Run The Workflow Manually

In GitHub:

1. Open `Actions`.
2. Open `Dockerhub`.
3. Click `Run workflow`.

The workflow file is:

```text
.github/workflows/dockerhub.yml
```

Before images are published, the workflow now runs the release validation job:

- CI and report-contract checks
- pinned headless frontend smoke/release gate
- the same traceability checks used by the local release gate

The image publish job starts only when that validation job is green. Dockerhub
credentials are still read only from GitHub Actions secrets.

This publishes:

```text
<dockerhub-user>/cloudshop-backend:latest
<dockerhub-user>/cloudshop-frontend:latest
<dockerhub-user>/cloudshop-backend:sha-<commit>
<dockerhub-user>/cloudshop-frontend:sha-<commit>
```

Check in Dockerhub:

```text
Dockerhub
-> Repositories
-> cloudshop-backend
-> Tags

Dockerhub
-> Repositories
-> cloudshop-frontend
-> Tags
```

You should see `latest` and `sha-...`.

## Publish A Named Version

Create and push a Git tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

This also publishes:

```text
<dockerhub-user>/cloudshop-backend:v1.0.0
<dockerhub-user>/cloudshop-frontend:v1.0.0
```

Use a named version when you want the EC2 demo to be stable and repeatable.
Use `latest` when you want EC2 to always pull the newest build.

## Choose Version On EC2

In `.env` on EC2:

```text
DOCKERHUB_USERNAME=your-dockerhub-user
IMAGE_TAG=latest
```

For a fixed release:

```text
IMAGE_TAG=v1.0.0
```

Then deploy:

```bash
FRONTEND_URL=http://<ec2-public-ip> BACKEND_URL=http://<ec2-public-ip>/api sh ./scripts/ec2-deploy.sh
```

The deploy script waits up to 120 seconds for the backend and frontend
container healthchecks before it runs the public smoke test. A failed health
check stops the deployment proof instead of presenting a partially started
release as healthy.

For real production deployment the script also requires a fixed `sha-*` or
`v*` image tag, a public origin, RDS settings, distinct strong JWT/owner keys,
and safe schema/CORS/reset flags. It validates the Compose file before pulling
images. `REQUIRE_IMMUTABLE_TAG=false` is reserved for an explicitly temporary
non-production demo.

## If The Workflow Fails

Common fixes:

```text
unauthorized: incorrect username or password
```

Check:

```text
DOCKERHUB_USERNAME is correct
DOCKERHUB_TOKEN is an access token, not your account password
The token has read/write access
```

```text
repository does not exist
```

Usually Dockerhub creates it on first push. If it does not, create these repositories manually:

```text
cloudshop-backend
cloudshop-frontend
```

```text
Docker build fails
```

Check:

```text
CI workflow passes first
frontend builds locally with npm run build
backend builds locally or in IntelliJ
```

## Presentation Notes

Show in this order:

```text
1. GitHub Actions -> Dockerhub workflow
2. Successful workflow run
3. Dockerhub backend image tags
4. Dockerhub frontend image tags
5. EC2 .env uses DOCKERHUB_USERNAME and IMAGE_TAG
6. EC2 deploy script pulls those images
```

Say:

```text
The CI workflow tests and builds the project.
The Dockerhub workflow publishes versioned Docker images.
EC2 pulls those images and runs them with environment variables that point to AWS RDS.
```

For the presentation, show GitHub Actions first, then Dockerhub images, then EC2 pulling those images.
