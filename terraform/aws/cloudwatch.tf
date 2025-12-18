resource "aws_cloudwatch_log_group" "ecs_logs" {
  name              = "/ecs/${local.name-env-region}"
  retention_in_days = 30
  tags              = local.tags
}

