################################################################################
# OSMT API Service

resource "aws_ecs_service" "osmt_api" {
  name             = "osmt-api"
  cluster          = aws_ecs_cluster.main.id
  task_definition  = aws_ecs_task_definition.osmt_api.arn
  desired_count    = 1
  launch_type      = "FARGATE"
  tags             = local.tags
  platform_version = "1.4.0"

  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.app_internal.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.osmt_api.arn
    container_name   = "osmt-api"
    container_port   = local.ports.osmt_api
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}

