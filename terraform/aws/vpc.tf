module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "6.5.1"

  name = var.config.vpc.name == null ? local.name-env : var.config.vpc.name
  cidr = var.config.vpc.cidr

  azs             = data.aws_availability_zones.zones.names
  private_subnets = [cidrsubnet(var.config.vpc.cidr, 6, 0), cidrsubnet(var.config.vpc.cidr, 6, 1), cidrsubnet(var.config.vpc.cidr, 6, 2)]
  public_subnets  = [cidrsubnet(var.config.vpc.cidr, 6, 13), cidrsubnet(var.config.vpc.cidr, 6, 14), cidrsubnet(var.config.vpc.cidr, 6, 15)]

  enable_nat_gateway = true
  enable_vpn_gateway = false
  single_nat_gateway = true

  tags = local.tags
}

