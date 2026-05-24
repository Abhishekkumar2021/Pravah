terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.16"

  name = "${var.name}-vpc"
  cidr = var.cidr

  azs             = var.azs
  private_subnets = [for i, az in var.azs : cidrsubnet(var.cidr, 4, i)]
  public_subnets  = [for i, az in var.azs : cidrsubnet(var.cidr, 4, i + length(var.azs))]

  enable_nat_gateway   = true
  single_nat_gateway   = var.single_nat_gateway
  enable_dns_hostnames = true
  enable_dns_support   = true

  public_subnet_tags = {
    "kubernetes.io/role/elb" = 1
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = 1
  }

  tags = var.tags
}
