################################################################################
# IAM
################################################################################

resource "aws_iam_role_policy_attachment" "ecs_task_execution_policy" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_policy_attachment" "ecs_logs_policy" {
  name       = "ecs-cloudwatch-logs"
  roles      = [aws_iam_role.ecs_task_execution.name, aws_iam_role.ecs_task.name]
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess"
}

# IAM role for ECS tasks
data "aws_iam_policy_document" "ecs_task_assume_role_policy" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "${local.name-env-region}-execution"
  tags = local.tags

  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role_policy.json
}

resource "aws_iam_role" "ecs_task" {
  name = local.name-env-region
  tags = local.tags

  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role_policy.json
}

# Policy Attachments for ECS Task Role
resource "aws_iam_role_policy_attachment" "s3_policy_attachment" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.s3_access.arn
}

resource "aws_iam_role_policy_attachment" "secrets_policy_attachment" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.secrets_access_policy.arn
}

resource "aws_iam_role_policy_attachment" "ssm_policy_attachment" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.ssm_read_access.arn
}

resource "aws_iam_role_policy_attachment" "certificate_and_loadbalancer_management_policy_attachment" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.certificate_and_loadbalancer_management.arn
}

# Policy Attachments for Execution Task Role

resource "aws_iam_role_policy_attachment" "execution_ssm_policy_attachment" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = aws_iam_policy.ssm_read_access.arn
}

