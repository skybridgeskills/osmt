################################################################################
# Application Load Balancer for OSMT API
################################################################################

resource "aws_lb" "osmt_api" {
  name               = "${local.name-env}-api"
  internal           = false
  load_balancer_type = "application"
  security_groups = [
    aws_security_group.web_external.id
  ]
  subnets = module.vpc.public_subnets
  tags    = local.tags
}

resource "aws_lb_listener" "osmt_api_https" {
  load_balancer_arn = aws_lb.osmt_api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-Res-2021-06"
  certificate_arn   = module.acm_wildcard_cert.acm_certificate_arn

  default_action {
    type = "fixed-response"
    fixed_response {
      status_code  = "403"
      content_type = "text/html"
      message_body = "<html><body><h1>403 Forbidden</h1></body></html>"
    }
  }
}

# Target Groups
resource "aws_lb_target_group" "osmt_api" {
  name        = "${local.name-env}-api"
  port        = local.ports.osmt_api
  protocol    = "HTTP"
  vpc_id      = module.vpc.vpc_id
  tags        = local.tags
  target_type = "ip"

  health_check {
    enabled             = true
    interval            = 30
    path                = "/actuator/health"
    port                = "traffic-port"
    healthy_threshold   = 3
    unhealthy_threshold = 5
    timeout             = 5
    matcher             = "200"
  }
}

# Listener Rules
resource "aws_lb_listener_rule" "osmt_api" {
  listener_arn = aws_lb_listener.osmt_api_https.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.osmt_api.arn
  }

  condition {
    host_header {
      values = [local.domains.osmt]
    }
  }
}

resource "aws_lb_listener_certificate" "osmt_api_listener_cert" {
  listener_arn    = aws_lb_listener.osmt_api_https.arn
  certificate_arn = module.acm_wildcard_cert.acm_certificate_arn
}

