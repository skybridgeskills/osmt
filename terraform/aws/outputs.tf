output "aws_lb__osmt_api" {
  value       = aws_lb.osmt_api
  description = "The OSMT API ALB"
}

output "aws_lb_listener__osmt_api_https" {
  value       = aws_lb_listener.osmt_api_https
  description = "The OSMT API ALB HTTPS listener"
}

output "osmt_domain" {
  value       = local.domains.osmt
  description = "OSMT domain name"
}

output "ecs_cluster_name" {
  value       = aws_ecs_cluster.main.name
  description = "ECS cluster name"
}

output "rds_endpoint" {
  value       = module.rds.db_instance_endpoint
  description = "MySQL RDS endpoint"
}

output "redis_endpoint" {
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
  description = "Redis ElastiCache endpoint"
}

output "opensearch_endpoint" {
  value       = aws_opensearch_domain.opensearch.endpoint
  description = "OpenSearch endpoint"
}

output "auth_mode" {
  value       = var.auth_mode
  description = "Authentication mode (single-auth or oauth2)"
}

output "single_auth_admin_password" {
  value       = local.auth.password
  description = "Single-auth admin password (only available when auth_mode is single-auth)"
  sensitive   = true
}

