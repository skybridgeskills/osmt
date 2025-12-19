locals {
  # grabs the short-form region code - see: https://docs.aws.amazon.com/global-infrastructure/latest/regions/aws-availability-zones.html
  region_code = split("-", data.aws_availability_zones.zones.zone_ids[0])[0]

  name-env        = "osmt-${var.config.env.name}"       # e.g. osmt-staging
  name-env-region = "${local.name-env}-${local.region_code}" # e.g. osmt-staging-usw2

  # when var.config.env.tld = "prettygoodskills.com"
  #   and var.config.env.name = "staging"
  base_domain = "${var.config.env.name}.${var.config.env.tld}"

  config = {
    email_from_address = var.config.app.from_address == null ? "no-reply-${var.config.env.name}@${var.config.env.tld}" : var.config.app.from_address
  }


  domains = {
    osmt = "osmt.${local.base_domain}"   # osmt.staging.prettygoodskills.com
  }

  ports = {
    # Apps
    osmt_api = 8080

    # Services
    redis    = 6379
    mysql    = 3306
  }

  mysql_uri = "mysql://${urlencode(jsondecode(data.aws_secretsmanager_secret_version.db_password.secret_string).username)}:${urlencode(jsondecode(data.aws_secretsmanager_secret_version.db_password.secret_string).password)}@${module.rds.db_instance_endpoint}/${var.config.rds.db_name}"
  redis_uri = "redis://${aws_elasticache_replication_group.redis.primary_endpoint_address}:${local.ports.redis}"
  opensearch_uri = "https://${aws_opensearch_domain.opensearch.endpoint}"

  # Computed auth password - use provided password or generated one
  auth = {
    password = var.auth_mode == "single-auth" ? (var.single_auth.admin_password != null ? var.single_auth.admin_password : random_password.single_auth_admin[0].result) : null
  }

  tags = merge(
    {
      account     = data.aws_caller_identity.current.account_id
      environment = var.config.env.name
      region      = data.aws_region.current.region
      service     = "osmt"
    },
    var.config.tags
  )
}

