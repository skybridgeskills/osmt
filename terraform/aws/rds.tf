module "rds" {
  # https://registry.terraform.io/modules/terraform-aws-modules/rds/aws/6.13.0
  source  = "terraform-aws-modules/rds/aws"
  version = "~> 6.13.0"

  identifier = var.config.rds.name == null ? local.name-env-region : var.config.rds.name

  engine            = "mysql"
  engine_version    = var.config.rds.initial_engine_version
  instance_class    = var.config.rds.instance_class
  allocated_storage = var.config.rds.allocated_storage

  db_name  = var.config.rds.db_name
  username = var.config.rds.db_user
  port     = local.ports.mysql

  iam_database_authentication_enabled = false

  vpc_security_group_ids = [aws_security_group.mysql_internal.id]

  backup_window      = "03:00-04:00"
  maintenance_window = "sun:04:00-sun:05:00"

  manage_master_user_password              = true
  master_user_secret_kms_key_id            = module.kms.key_id
  manage_master_user_password_rotation     = false

  snapshot_identifier = var.config.rds.restore_snapshot_identifier

  # Enhanced Monitoring - see example for details on how to create the role
  # by yourself, in case you don't want to create it automatically
  # monitoring_interval    = "30"
  # monitoring_role_name   = "MyRDSMonitoringRole"
  # create_monitoring_role = true

  # DB subnet group
  create_db_subnet_group = true
  subnet_ids             = module.vpc.private_subnets

  # DB parameter group
  family = "mysql${split(".", var.config.rds.initial_engine_version)[0]}"

  # DB option group
  major_engine_version = split(".", var.config.rds.initial_engine_version)[0]

  # Database Deletion Protection
  deletion_protection = var.config.rds.deletion_protection
  tags                = local.tags
}

