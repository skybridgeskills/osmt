# OSMT AWS Infrastructure

This directory contains the Terraform configuration for deploying OSMT to AWS using ECS Fargate.

## Architecture

The infrastructure includes:
- **VPC** with public and private subnets
- **MySQL RDS** database instance
- **Redis ElastiCache** for caching
- **AWS OpenSearch** for search functionality
- **ECS Fargate** cluster with OSMT API service
- **Application Load Balancer** with HTTPS termination
- **Route53** DNS records
- **ACM** SSL certificates

## File Organization

The Terraform files are organized by logical function to make it easy to understand which resources belong to each service and to copy/paste service configurations when adding new services.

### Service-Specific Files (`api_*`)

These files contain API-specific resources and can be copy/pasted to create new services:

- `api_ecs_cluster.tf` - ECS cluster (shared infrastructure)
- `api_ecs_task_def.tf` - ECS task definition
- `api_ecs_service.tf` - ECS service
- `api_cloudwatch.tf` - CloudWatch log group for ECS
- `api_alb.tf` - Application Load Balancer (API-specific)
- `api_route53.tf` - Route53 DNS record pointing to API ALB
- `api_iam.tf` - IAM roles for ECS tasks
- `api_iam_policies.tf` - IAM policies for ECS tasks
- `api_security_groups.tf` - Security groups for API service (`app_internal`, `web_external`)

### Shared Infrastructure Files (un-prefixed)

These files contain infrastructure shared across all services:

- `vpc.tf` - VPC and networking infrastructure
- `acm.tf` - SSL certificates
- `rds.tf` - MySQL database
- `elasticache.tf` - Redis cache
- `opensearch.tf` - OpenSearch
- `security_groups.tf` - Data layer security groups (`redis_internal`, `mysql_internal`, `opensearch_internal`)

### Configuration Files (un-prefixed)

- `variables.tf` - Input variables
- `locals.tf` - Local values and computed expressions
- `outputs.tf` - Output values
- `data.tf` - Terraform data sources
- `secrets.tf` - Random secrets generation
- `ssm.tf` - SSM parameters
- `kms.tf` - KMS encryption keys
- `versions.tf` - Terraform version constraints

This organization makes it easy to:
1. Identify which resources belong to which service
2. Copy/paste service files to create new services (e.g., `ui_*` files for a UI service)
3. Understand which infrastructure is shared vs service-specific

## Prerequisites

Before deploying, ensure you have:

1. **AWS Account** with appropriate permissions
2. **ECR Repository** for Docker images
3. **Route53 Hosted Zone** for `staging.prettygoodskills.com`
4. **SendGrid API Key** for email functionality

## ECR Repository Setup

### Manual ECR Repository Creation

Create an ECR repository for OSMT images:

```bash
# Create ECR repository
aws ecr create-repository \
  --repository-name osmt \
  --region us-west-2

# Get repository URI
aws ecr describe-repositories \
  --repository-names osmt \
  --region us-west-2 \
  --query 'repositories[0].repositoryUri' \
  --output text
```

The repository URI will be something like: `123456789012.dkr.ecr.us-west-2.amazonaws.com/osmt`

### Docker Image Build and Push

Build and push the OSMT Docker image:

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-west-2.amazonaws.com

# Build the API image (includes UI)
docker build -t osmt:latest -f api/Dockerfile .

# Tag the image
docker tag osmt:latest 123456789012.dkr.ecr.us-west-2.amazonaws.com/osmt:latest

# Push the image
docker push 123456789012.dkr.ecr.us-west-2.amazonaws.com/osmt:latest
```

For versioned releases, tag with version numbers:

```bash
# Tag with version
docker tag osmt:latest 123456789012.dkr.ecr.us-west-2.amazonaws.com/osmt:v1.2.3

# Push versioned image
docker push 123456789012.dkr.ecr.us-west-2.amazonaws.com/osmt:v1.2.3
```

## Deployment Process

### 1. Initial Infrastructure Setup

```bash
# Initialize Terraform
terraform init

# Plan the deployment
terraform plan

# Apply the infrastructure
terraform apply
```

### 2. Database Migration

Run database migrations using the deployment script:

```bash
cd scripts
pnpm install
pnpm tsx deploy-migrations.script.ts staging latest
```

### 3. Service Deployment

Deploy the OSMT API service:

```bash
cd scripts
pnpm tsx deploy-service.script.ts staging osmt-api latest
```

### 4. Initial Data Setup (Optional)

Load demo data for testing:

```bash
cd scripts
pnpm tsx deploy-initial-data.script.ts staging
```

## Configuration

### Environment Variables

The ECS service is configured with environment variables from SSM Parameter Store:

- `DB_URI`: MySQL database connection string
- `REDIS_URI`: Redis connection string
- `OPENSEARCH_URI`: OpenSearch endpoint
- `OAUTH_*`: Okta OAuth configuration

### Secrets Management

Sensitive configuration is stored in AWS Secrets Manager:
- Database credentials (auto-managed by RDS)
- SendGrid API key
- OAuth client secrets

## Monitoring

- **CloudWatch Logs**: ECS container logs are sent to CloudWatch
- **ALB Access Logs**: Load balancer access logs
- **RDS Monitoring**: Enhanced monitoring enabled
- **Health Checks**: ALB health checks on `/actuator/health`

## Security

- **VPC**: Resources deployed in private subnets
- **Security Groups**: Least-privilege access rules
- **IAM**: Task execution and task roles with minimal permissions
- **HTTPS**: SSL/TLS termination at ALB
- **Encryption**: Data at rest encryption for RDS and Redis

## Cost Optimization

For staging environment:
- **t3.micro** instances for RDS and Redis
- **t3.small.search** for OpenSearch
- **Fargate** with minimal CPU/memory allocation
- **On-demand** pricing (no reserved instances)

## Troubleshooting

### Common Issues

1. **ECR Image Not Found**: Ensure the image is pushed to ECR and the tag is correct
2. **Database Connection**: Check security groups allow ECS to access RDS
3. **Health Check Failures**: Verify the application starts correctly
4. **Migration Failures**: Check Flyway migration scripts and database connectivity

### Logs

View logs in CloudWatch:
- ECS logs: `/ecs/osmt-staging`
- ALB logs: S3 bucket (if enabled)

### Debugging

Connect to ECS tasks using ECS Exec for debugging:

```bash
# Enable execute command on service
aws ecs update-service \
  --cluster osmt-staging \
  --service osmt-api \
  --enable-execute-command \
  --region us-west-2

# Execute command in running task
aws ecs execute-command \
  --cluster osmt-staging \
  --task <task-id> \
  --container osmt-api \
  --interactive \
  --command "/bin/bash" \
  --region us-west-2
```

## Cleanup

To destroy the infrastructure:

```bash
terraform destroy
```

**Warning**: This will delete all data including the database. Ensure you have backups if needed.

