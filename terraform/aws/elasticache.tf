resource "aws_elasticache_subnet_group" "default" {
  name       = local.name-env-region
  subnet_ids = module.vpc.private_subnets
  tags       = local.tags
}

# Redis
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = var.config.cache.name == null ? local.name-env-region : var.config.cache.name
  description          = "Redis replication group"
  node_type            = var.config.cache.node_type
  port                 = local.ports.redis
  parameter_group_name = var.config.cache.parameter_group_name

  # Defaults to equal the number of private subnets
  num_cache_clusters         = var.config.cache.number_of_nodes == null ? length(module.vpc.private_subnets) : var.config.cache.number_of_nodes
  security_group_ids         = [aws_security_group.redis_internal.id]
  subnet_group_name          = aws_elasticache_subnet_group.default.name
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  tags                       = local.tags
}

