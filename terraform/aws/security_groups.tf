resource "aws_security_group" "web_external" {
  name        = "${local.name-env}-web_external"
  description = "Allow tcp/80,tcp/443 from all"
  vpc_id      = module.vpc.vpc_id
  tags        = merge(local.tags, { Name = "${local.name-env}-web_external" })

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_security_group" "app_internal" {
  name        = "${local.name-env}-app_internal"
  description = "Allow internal traffic for app services"
  vpc_id      = module.vpc.vpc_id
  tags        = merge(local.tags, { Name = "${local.name-env}-app_internal" })

  ingress {
    from_port       = local.ports.osmt_api
    to_port         = local.ports.osmt_api
    protocol        = "tcp"
    security_groups = [aws_security_group.web_external.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "redis_internal" {
  name        = "${local.name-env}-redis_internal"
  description = "Allow tcp/6379 from private subnets"
  vpc_id      = module.vpc.vpc_id
  tags        = merge(local.tags, { Name = "${local.name-env}-redis_internal" })

  ingress {
    from_port   = local.ports.redis
    to_port     = local.ports.redis
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "mysql_internal" {
  name        = "${local.name-env}-mysql_internal"
  description = "Allow tcp/3306 from private subnets"
  vpc_id      = module.vpc.vpc_id
  tags        = merge(local.tags, { Name = "${local.name-env}-mysql_internal" })

  ingress {
    from_port   = local.ports.mysql
    to_port     = local.ports.mysql
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "opensearch_internal" {
  name        = "${local.name-env}-opensearch_internal"
  description = "Allow tcp/443 from private subnets"
  vpc_id      = module.vpc.vpc_id
  tags        = merge(local.tags, { Name = "${local.name-env}-opensearch_internal" })

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

