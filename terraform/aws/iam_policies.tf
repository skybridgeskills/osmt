resource "aws_iam_policy" "s3_access" {
  name        = "${local.name-env-region}-s3-access"
  description = "Allows access to S3 buckets for file storage"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::osmt-*",
          "arn:aws:s3:::osmt-*/*"
        ]
      }
    ]
  })
}

resource "aws_iam_policy" "secrets_access_policy" {
  name        = "${local.name-env-region}-secrets-access"
  description = "Allows access to secrets in Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          "arn:aws:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:platform/secrets/*",
          "arn:aws:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:osmt/*",
          module.rds.db_instance_master_user_secret_arn
        ]
      }
    ]
  })
}

resource "aws_iam_policy" "platform_secrets_access_policy" {
  name        = "${local.name-env-region}-platform-secrets-access"
  description = "Allows access to platform secrets for task execution"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          "arn:aws:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:platform/secrets/*",
          module.rds.db_instance_master_user_secret_arn
        ]
      }
    ]
  })
}

resource "aws_iam_policy" "ssm_read_access" {
  name        = "${local.name-env-region}-ssm-read-access"
  description = "Allows read access to SSM parameters"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter/osmt/*"
      }
    ]
  })
}

resource "aws_iam_policy" "certificate_and_loadbalancer_management" {
  name        = "${local.name-env-region}-certificate-lb-management"
  description = "Allows management of certificates and load balancers"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "acm:DescribeCertificate",
          "acm:GetCertificate",
          "acm:ListCertificates",
          "elasticloadbalancing:DescribeLoadBalancers",
          "elasticloadbalancing:DescribeTargetGroups",
          "elasticloadbalancing:DescribeListeners",
          "elasticloadbalancing:ModifyListener",
          "elasticloadbalancing:ModifyTargetGroup"
        ]
        Resource = "*"
      }
    ]
  })
}

