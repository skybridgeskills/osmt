resource "aws_route53_record" "osmt_api" {
  zone_id = data.aws_route53_zone.default.zone_id
  name    = local.domains.osmt
  type    = "A"

  alias {
    name                   = aws_lb.osmt_api.dns_name
    zone_id                = aws_lb.osmt_api.zone_id
    evaluate_target_health = true
  }
}

