module "kms" {
  source  = "terraform-aws-modules/kms/aws"
  version = "~> 2.0"

  description             = "KMS key for OSMT encryption"
  deletion_window_in_days = 7
  key_usage               = "ENCRYPT_DECRYPT"

  # Policy
  key_statements = [
    {
      sid = "Enable IAM User Permissions"
      principals = [
        {
          type = "AWS"
          identifiers = [
            "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
          ]
        }
      ]
      actions = [
        "kms:*"
      ]
      resources = ["*"]
    },
    {
      sid = "Allow ECS Tasks"
      principals = [
        {
          type = "AWS"
          identifiers = [
            aws_iam_role.ecs_task_execution.arn,
            aws_iam_role.ecs_task.arn
          ]
        }
      ]
      actions = [
        "kms:Decrypt",
        "kms:DescribeKey",
        "kms:Encrypt",
        "kms:GenerateDataKey*",
        "kms:ReEncrypt*"
      ]
      resources = ["*"]
    }
  ]

  # Aliases
  aliases = ["${local.name-env-region}"]

  tags = local.tags
}

