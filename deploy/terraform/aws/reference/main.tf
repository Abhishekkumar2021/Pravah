locals {
  name = "${var.cluster_name}-${var.environment}"
}

resource "random_password" "postgres" {
  length  = 32
  special = false
}

resource "random_password" "redis" {
  length  = 32
  special = false
}

module "network" {
  source = "../../modules/network"

  name               = local.name
  cidr               = var.vpc_cidr
  azs                = var.availability_zones
  single_nat_gateway = var.single_nat_gateway
  tags               = var.tags
}

module "eks" {
  source = "../../modules/eks"

  name               = local.name
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  tags               = var.tags
}

module "rds" {
  source = "../../modules/rds"

  name                       = local.name
  vpc_id                     = module.network.vpc_id
  private_subnet_ids         = module.network.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  password                   = random_password.postgres.result
  tags                       = var.tags
}

module "elasticache" {
  source = "../../modules/elasticache"

  name                       = local.name
  vpc_id                     = module.network.vpc_id
  private_subnet_ids         = module.network.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  auth_token                 = random_password.redis.result
  tags                       = var.tags
}

module "artifacts" {
  source = "../../modules/s3"

  name        = local.name
  bucket_name = var.artifacts_bucket_name
  tags        = var.tags
}

module "msk" {
  count  = var.enable_msk ? 1 : 0
  source = "../../modules/msk"

  name                       = local.name
  vpc_id                     = module.network.vpc_id
  private_subnet_ids         = module.network.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  tags                       = var.tags
}
