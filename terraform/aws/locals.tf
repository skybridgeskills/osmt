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

  # SendGrid domain verification configuration
  sendgrid_domains = {
    for domain_config in var.config.sendgrid.domains : domain_config.domain => {
      domain  = domain_config.domain
      records = domain_config.records
    }
  }

  cloudfront = {
    aliases = length(var.config.cloudfront.alternate_domains) == 0 ? [
      local.domains.osmt
    ] : var.config.cloudfront.alternate_domains

    cors_origins = length(var.config.cloudfront.alternate_domains) == 0 ? [
      "https://${local.base_domain}",
      "https://${local.domains.osmt}",
    ] : [for domain in var.config.cloudfront.alternate_domains : "https://${domain}"]
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

