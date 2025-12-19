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

# OAuth2 parameters - only created when auth_mode is oauth2
resource "aws_ssm_parameter" "oauth_issuer" {
  count = var.auth_mode == "oauth2" ? 1 : 0
  name  = "/osmt/${var.config.env.name}/oauth_issuer"
  type  = "SecureString"
  value = var.oauth2.issuer != null ? var.oauth2.issuer : "https://wgu.okta.com/oauth2/default"
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_clientid" {
  count = var.auth_mode == "oauth2" ? 1 : 0
  name  = "/osmt/${var.config.env.name}/oauth_clientid"
  type  = "SecureString"
  value = var.oauth2.client_id != null ? var.oauth2.client_id : "placeholder-client-id"
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_clientsecret" {
  count = var.auth_mode == "oauth2" ? 1 : 0
  name  = "/osmt/${var.config.env.name}/oauth_clientsecret"
  type  = "SecureString"
  value = var.oauth2.client_secret != null ? var.oauth2.client_secret : "placeholder-client-secret"
  tags  = local.tags
}

resource "aws_ssm_parameter" "oauth_audience" {
  count = var.auth_mode == "oauth2" ? 1 : 0
  name  = "/osmt/${var.config.env.name}/oauth_audience"
  type  = "SecureString"
  value = var.oauth2.audience != null ? var.oauth2.audience : "placeholder-audience"
  tags  = local.tags
}

# Single-auth parameters - only created when auth_mode is single-auth
resource "aws_ssm_parameter" "single_auth_admin_username" {
  count = var.auth_mode == "single-auth" ? 1 : 0
  name  = "/osmt/${var.config.env.name}/single_auth_admin_username"
  type  = "String"
  value = var.single_auth.admin_username
  tags  = local.tags
}

resource "aws_ssm_parameter" "single_auth_admin_password" {
  count = var.auth_mode == "single-auth" ? 1 : 0
  name  = "/osmt/${var.config.env.name}/single_auth_admin_password"
  type  = "SecureString"
  value = var.single_auth.admin_password != null ? var.single_auth.admin_password : random_password.single_auth_admin[0].result
  tags  = local.tags
}

# Auth mode parameter - always created
resource "aws_ssm_parameter" "auth_mode" {
  name  = "/osmt/${var.config.env.name}/auth_mode"
  type  = "String"
  value = var.auth_mode
  tags  = local.tags
}

