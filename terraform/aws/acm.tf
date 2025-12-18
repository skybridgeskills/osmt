module "acm_wildcard_cert" {
  source  = "terraform-aws-modules/acm/aws"
  version = "~> 5.0"

  domain_name = "*.${local.base_domain}"
  zone_id     = data.aws_route53_zone.default.zone_id

  subject_alternative_names = [
    local.base_domain,
    local.domains.osmt
  ]

  wait_for_validation = true

  tags = local.tags
}

