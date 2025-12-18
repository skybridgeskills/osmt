resource "aws_ssm_parameter" "mysql_uri" {
  name  = "/osmt/${var.config.env.name}/mysql_uri"
  type  = "SecureString"
  value = local.mysql_uri
  tags  = local.tags
}

resource "aws_ssm_parameter" "redis_uri" {
  name  = "/osmt/${var.config.env.name}/redis_uri"
  type  = "SecureString"
  value = local.redis_uri
  tags  = local.tags
}

resource "aws_ssm_parameter" "opensearch_uri" {
  name  = "/osmt/${var.config.env.name}/opensearch_uri"
  type  = "SecureString"
  value = local.opensearch_uri
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_issuer" {
  name  = "/osmt/${var.config.env.name}/oauth_issuer"
  type  = "SecureString"
  value = "https://wgu.okta.com/oauth2/default"
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_clientid" {
  name  = "/osmt/${var.config.env.name}/oauth_clientid"
  type  = "SecureString"
  value = "placeholder-client-id"
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_clientsecret" {
  name  = "/osmt/${var.config.env.name}/oauth_clientsecret"
  type  = "SecureString"
  value = "placeholder-client-secret"
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_audience" {
  name  = "/osmt/${var.config.env.name}/oauth_audience"
  type  = "SecureString"
  value = "placeholder-audience"
  tags  = local.tags
}

