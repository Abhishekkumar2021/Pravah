variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (e.g. staging, production)"
  type        = string
  default     = "staging"
}

variable "cluster_name" {
  description = "EKS cluster and resource name prefix"
  type        = string
  default     = "pravah"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Two or more AZs in the target region"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "enable_msk" {
  description = "Provision Amazon MSK (adds significant cost; disable for cost-sensitive staging)"
  type        = bool
  default     = false
}

variable "artifacts_bucket_name" {
  description = "Globally unique S3 bucket name for execution artifacts"
  type        = string
}

variable "single_nat_gateway" {
  description = "Single NAT gateway for lower cost in non-prod"
  type        = bool
  default     = true
}

variable "tags" {
  type    = map(string)
  default = {}
}
