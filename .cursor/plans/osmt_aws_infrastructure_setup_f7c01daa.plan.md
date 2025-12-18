---
name: OSMT AWS Infrastructure Setup
overview: Set up Terraform infrastructure for deploying OSMT to AWS staging, including ECS services, MySQL RDS, Redis ElastiCache, OpenSearch, ALB, Route53, GitHub Actions for deployment, database migrations, and initial demo data setup.
todos:
  - id: terraform-structure
    content: Create terraform/aws/ directory structure with all Terraform files (versions, variables, locals, data sources, VPC, RDS MySQL, ElastiCache, OpenSearch, ECS, ALB, security groups, IAM, secrets, Route53, ACM, CloudWatch, outputs)
    status: pending
  - id: deployment-scripts
    content: Create TypeScript deployment scripts (deploy-migrations.script.ts, deploy-service.script.ts, deploy-initial-data.script.ts) following monorepo patterns
    status: pending
  - id: github-actions
    content: Create .github/workflows/deploy.yml workflow with migrate, deploy-api, and setup-initial-data jobs
    status: pending
  - id: infra-integration
    content: Add OSMT module/workspace reference to /Users/yona/dev/skybridge/infra/accounts/skybridgeskills-staging/core/osmt.tf
    status: pending
  - id: package-json
    content: Update package.json with deployment script dependencies (@aws-sdk/client-ecs, etc.) if not already present
    status: pending
  - id: ecr-setup
    content: Document ECR repository creation and image push process (may need manual setup or separate script)
    status: pending
---

# OSMT AWS Infrastructure Setup

## Overview

This plan sets up AWS infrastructure for deploying OSMT to staging, following patterns from the skybridgeskills-monorepo. The infrastructure includes ECS Fargate services, MySQL RDS, Redis ElastiCache, OpenSearch, Application Load Balancer, Route53 DNS, and GitHub Actions workflows for automated deployment.

## Architecture

- **API Service**: Spring Boot application running on ECS Fargate (serves both API and embedded UI)
- **Database**: MySQL RDS (OSMT uses MySQL, not PostgreSQL)
- **Cache**: Redis ElastiCache
- **Search**: AWS OpenSearch Service
- **Networking**: VPC with public/private subnets, ALB for external access
- **DNS**: Route53 hosted zone for osmt.staging.prettygoodskills.com
- **Deployment**: GitHub Actions workflows for migrations and service deployment

## Implementation Steps

### 1. Create Terraform Infrastructure (`terraform/aws/`)

Create Terraform modules following the monorepo pattern:

- **`versions.tf`**: Terraform and provider versions
- **`variables.tf`**: Configuration variables (environment, RDS settings, cache settings, etc.)
- **`locals.tf`**: Local values (naming conventions, ports, domains)
- **`data.tf`**: Data sources (AWS region, availability zones, Route53 zone, secrets)
- **`vpc.tf`**: VPC module with public/private subnets
- **`rds.tf`**: MySQL RDS instance (using terraform-aws-modules/rds/aws, adapted for MySQL)
- **`elasticache.tf`**: Redis ElastiCache replication group
- **`opensearch.tf`**: AWS OpenSearch Service domain
- **`ecs.tf`**: ECS cluster
- **`ecs_task_definitions.tf`**: ECS task definitions for API service
- **`ecs_services.tf`**: ECS service for API
- **`alb.tf`**: Application Load Balancer with HTTPS listener
- **`security_groups.tf`**: Security groups for ALB, ECS, RDS, Redis, OpenSearch
- **`iam.tf`**: IAM roles and policies for ECS tasks
- **`iam_policies.tf`**: Custom IAM policies (secrets access, SSM, etc.)
- **`secrets.tf`**: Secrets Manager resources
- **`ssm.tf`**: SSM Parameter Store resources
- **`kms.tf`**: KMS keys for encryption
- **`route53.tf`**: Route53 records pointing to ALB
- **`acm.tf`**: ACM certificate for HTTPS
- **`cloudwatch.tf`**: CloudWatch log groups
- **`outputs.tf`**: Output values (ALB DNS, cluster name, etc.)

Key differences from monorepo:

- Use MySQL engine instead of PostgreSQL in RDS
- Add OpenSearch domain configuration
- Single ECS service (API) instead of multiple services
- UI is embedded in the API service (no separate UI deployment)

### 2. Create Deployment Scripts

Create TypeScript deployment scripts similar to monorepo:

- **`scripts/deploy-migrations.script.ts`**: Runs Flyway migrations as ECS task
- Registers migration task definition
- Runs one-off ECS task with migration container
- Monitors logs and task completion
- Uses Spring Boot with Flyway enabled

- **`scripts/deploy-service.script.ts`**: Deploys API service
- Updates ECS task definition with new image version
- Updates ECS service to use new task definition
- Waits for service to stabilize

- **`scripts/deploy-initial-data.script.ts`**: Loads initial demo data
- Runs one-off ECS task to execute SQL file
- Loads `test/sql/fixed_ci_dataset.sql` into MySQL
- Only runs on first deployment or when explicitly requested

### 3. Create GitHub Actions Workflow (`.github/workflows/deploy.yml`)

Workflow with three jobs:

1. **`migrate`**: Runs database migrations

- Checks out code
- Configures AWS credentials
- Runs `deploy-migrations.script.ts`
- Creates GitHub deployment status

2. **`deploy-api`**: Deploys API service

- Depends on `migrate`
- Runs `deploy-service.script.ts`
- Creates GitHub deployment status

3. **`setup-initial-data`** (optional, manual trigger): Loads demo data

- Runs `deploy-initial-data.script.ts`
- Only runs when explicitly triggered

Workflow supports:

- Manual dispatch with environment and version inputs
- Reusable workflow call pattern
- GitHub deployment tracking

### 4. Update Main Infrastructure (`/Users/yona/dev/skybridge/infra/accounts/skybridgeskills-staging/core/`)

Add OSMT workspace/module to main infra:

- **`osmt.tf`**: Module call or workspace reference to OSMT terraform
- Update **`route53.tf`**: Add OSMT subdomain delegation if needed
- Update **`locals.tf`**: Add OSMT-related local values if needed

### 5. Docker Image Configuration

Ensure Docker images are built and pushed to ECR:

- API Dockerfile already builds UI into the JAR
- Need ECR repository for OSMT images
- Image tagging strategy (version-based)

### 6. Environment Configuration

Create environment-specific configuration:

- SSM parameters for database URIs, Redis URIs, OpenSearch endpoints
- Secrets Manager for sensitive values (OAuth credentials, database passwords)
- Environment variables for ECS tasks

### 7. Migration Handling

Migrations run via Flyway:

- Spring Boot application with `spring.flyway.enabled=true`
- Migration files in `api/src/main/resources/db/migration/`
- Migration task runs as one-off ECS task before service deployment
- Uses same Docker image as service but with migration command

### 8. Initial Data Setup

Demo data loading:

- SQL file: `test/sql/fixed_ci_dataset.sql`
- Runs as one-off ECS task
- Connects to MySQL RDS
- Executes SQL file
- Separate from migrations (migrations handle schema, this handles data)

## Files to Create

### Terraform Files

- `terraform/aws/versions.tf`
- `terraform/aws/variables.tf`
- `terraform/aws/locals.tf`
- `terraform/aws/data.tf`
- `terraform/aws/vpc.tf`
- `terraform/aws/rds.tf` (MySQL)
- `terraform/aws/elasticache.tf`
- `terraform/aws/opensearch.tf`
- `terraform/aws/ecs.tf`
- `terraform/aws/ecs_task_definitions.tf`
- `terraform/aws/ecs_services.tf`
- `terraform/aws/alb.tf`
- `terraform/aws/security_groups.tf`
- `terraform/aws/iam.tf`
- `terraform/aws/iam_policies.tf`
- `terraform/aws/secrets.tf`
- `terraform/aws/ssm.tf`
- `terraform/aws/kms.tf`
- `terraform/aws/route53.tf`
- `terraform/aws/acm.tf`
- `terraform/aws/cloudwatch.tf`
- `terraform/aws/outputs.tf`

### Deployment Scripts

- `scripts/deploy-migrations.script.ts`
- `scripts/deploy-service.script.ts`
- `scripts/deploy-initial-data.script.ts`

### GitHub Actions

- `.github/workflows/deploy.yml`

### Infrastructure Integration

- `/Users/yona/dev/skybridge/infra/accounts/skybridgeskills-staging/core/osmt.tf`

## Key Considerations

1. **MySQL vs PostgreSQL**: Adapt RDS module for MySQL engine
2. **OpenSearch**: Configure domain with appropriate instance types and access policies
3. **UI Embedding**: UI is already embedded in API JAR, no separate deployment needed
4. **Migrations**: Run as separate ECS task before service deployment
5. **Initial Data**: Separate script for loading demo data (not part of migrations)
6. **Secrets**: Use AWS Secrets Manager for sensitive configuration
7. **Networking**: Private subnets for RDS/Redis/OpenSearch, public subnets for ALB
8. **Health Checks**: ALB health checks on API health endpoint
9. **Logging**: CloudWatch logs for ECS tasks
10. **Cost Optimization**: Use appropriate instance sizes for staging environment

## Dependencies

- Existing monorepo deployment scripts as reference
- AWS account with appropriate permissions
- ECR repository for OSMT Docker images
- GitHub repository with Actions enabled
- Terraform Cloud workspace or local Terraform state management