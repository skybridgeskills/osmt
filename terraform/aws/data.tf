data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "zones" {
  state = "available"
}

data "aws_route53_zone" "default" {
  name = local.base_domain
}

data "aws_iam_roles" "administrators" {
  name_regex = "AWSReservedSSO_AdministratorAccess_*"
}

// Load the DB password
data "aws_secretsmanager_secret" "db_password" {
  name = module.rds.db_instance_master_user_secret_arn
}

data "aws_secretsmanager_secret_version" "db_password" {
  secret_id = data.aws_secretsmanager_secret.db_password.id
}


