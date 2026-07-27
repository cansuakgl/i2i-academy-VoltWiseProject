# WattSmart AWS Deployment

This AWS deployment is shaped for a low-cost demo account:

- Amazon RDS PostgreSQL stores durable application data.
- One EC2 instance runs Docker containers for the backend core, simulator, Kafka, and Ignite.
- Amazon ECR stores the backend/simulator image.
- An ALB forwards public API traffic to the EC2 backend container.
- S3 and CloudFront host the frontend, with `/api/*` routed to the ALB.

Kafka is intentionally not Amazon MSK because MSK is unavailable on some free-tier/demo accounts.

## Required `.env` Values

Keep secrets in the repo root `.env`; do not commit it.

```env
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

TF_VAR_db_username=postgres
TF_VAR_db_password=

GEMINI_API_KEY=
RESEND_API_KEY=
EMAIL_PROVIDER=logging
EMAIL_FROM=

TF_VAR_deploy_services=true
TF_VAR_deploy_simulator=true
TF_VAR_upload_frontend=true
TF_VAR_image_tag=latest
TF_VAR_ec2_instance_type=t3.small
TF_VAR_ec2_volume_gb=30
```

## Apply Infrastructure

```powershell
cd C:\Users\PC1\Desktop\VoltWise\src\infrastructure\aws
terraform init
terraform apply
```

## Build And Push Images

```powershell
cd C:\Users\PC1\Desktop\VoltWise
$region = "us-east-1"
$backendRepo = terraform -chdir=src\infrastructure\aws output -raw backend_ecr_repository_url
$simulatorRepo = terraform -chdir=src\infrastructure\aws output -raw simulator_ecr_repository_url
$registry = $backendRepo.Split("/")[0]

aws ecr get-login-password --region $region | docker login --username AWS --password-stdin $registry
docker build -f src\backend\Dockerfile -t "$backendRepo`:latest" src
docker tag "$backendRepo`:latest" "$simulatorRepo`:latest"
docker push "$backendRepo`:latest"
docker push "$simulatorRepo`:latest"
```

If the EC2 instance already exists and you pushed a new image, rerun:

```powershell
cd C:\Users\PC1\Desktop\VoltWise\src\infrastructure\aws
terraform apply -replace=aws_instance.app_host[0]
```

## Build And Upload Frontend

```powershell
cd C:\Users\PC1\Desktop\VoltWise\src\frontend
npm.cmd run build

cd C:\Users\PC1\Desktop\VoltWise\src\infrastructure\aws
terraform apply
```

Invalidate CloudFront after frontend changes:

```powershell
cd C:\Users\PC1\Desktop\VoltWise
$distributionId = terraform -chdir=src\infrastructure\aws output -raw frontend_cloudfront_distribution_id
aws cloudfront create-invalidation --distribution-id $distributionId --paths "/*"
```

## Useful Checks

```powershell
cd C:\Users\PC1\Desktop\VoltWise
terraform -chdir=src\infrastructure\aws output
```

Backend health:

```powershell
cd C:\Users\PC1\Desktop\VoltWise
$api = terraform -chdir=src\infrastructure\aws output -raw backend_alb_url
Invoke-WebRequest "$api/health"
```

Frontend URL:

```powershell
cd C:\Users\PC1\Desktop\VoltWise
terraform -chdir=src\infrastructure\aws output -raw frontend_cloudfront_url
```

Demo login should work through the CloudFront URL using the seeded admin credentials.
