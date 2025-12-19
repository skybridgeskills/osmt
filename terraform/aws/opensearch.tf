resource "aws_opensearch_domain" "opensearch" {
  domain_name    = local.name-env-region
  engine_version = "OpenSearch_2.11"

  cluster_config {
    instance_type          = var.config.opensearch.instance_type
    instance_count         = var.config.opensearch.instance_count
    dedicated_master_type  = var.config.opensearch.dedicated_master_type
    dedicated_master_count = var.config.opensearch.master_instance_count

    zone_awareness_enabled = true
    zone_awareness_config {
      availability_zone_count = length(module.vpc.private_subnets)
    }
  }

  vpc_options {
    subnet_ids         = slice(module.vpc.private_subnets, 0, var.config.opensearch.instance_count)
    security_group_ids = [aws_security_group.opensearch_internal.id]
  }

  ebs_options {
    ebs_enabled = true
    volume_size = var.config.opensearch.volume_size
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }

  advanced_security_options {
    enabled                        = true
    internal_user_database_enabled = true
    master_user_options {
      master_user_name     = "admin"
      master_user_password = random_password.opensearch_master_password.result
    }
  }

  access_policies = data.aws_iam_policy_document.opensearch_access.json

  tags = local.tags
}

data "aws_iam_policy_document" "opensearch_access" {
  statement {
    effect = "Allow"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["es:*"]
    resources = ["arn:aws:es:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:domain/${local.name-env-region}/*"]

    condition {
      test     = "IpAddress"
      variable = "aws:SourceIp"
      values   = ["0.0.0.0/0"]  # Allow from VPC - controlled by security groups
    }
  }
}

resource "random_password" "opensearch_master_password" {
  length  = 16
  special = true
}

