resource "aws_ecs_task_definition" "osmt_api" {
  family                   = "osmt-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  tags                     = local.tags

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name  = "osmt-api"
      image = "${var.ecr_registry}/osmt:latest"
      portMappings = [
        {
          containerPort = local.ports.osmt_api
          protocol      = "tcp"
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs_logs.name
          awslogs-region        = data.aws_region.current.region
          awslogs-stream-prefix = "osmt-api"
        }
      }
      environment = [
        { name = "APP_NAME", value = "osmt-api" },
        { name = "ENV_NAME", value = var.config.env.name },
      ]
      secrets = [
        {
          name      = "DB_URI"
          valueFrom = "${aws_ssm_parameter.mysql_uri.arn}"
        },
        {
          name      = "REDIS_URI"
          valueFrom = "${aws_ssm_parameter.redis_uri.arn}"
        },
        {
          name      = "OPENSEARCH_URI"
          valueFrom = "${aws_ssm_parameter.opensearch_uri.arn}"
        },
        {
          name      = "OAUTH_ISSUER"
          valueFrom = "${aws_ssm_parameter.oauth_issuer.arn}"
        },
        {
          name      = "OAUTH_CLIENTID"
          valueFrom = "${aws_ssm_parameter.oauth_clientid.arn}"
        },
        {
          name      = "OAUTH_CLIENTSECRET"
          valueFrom = "${aws_ssm_parameter.oauth_clientsecret.arn}"
        },
        {
          name      = "OAUTH_AUDIENCE"
          valueFrom = "${aws_ssm_parameter.oauth_audience.arn}"
        },
      ]
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}

