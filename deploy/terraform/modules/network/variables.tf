variable "name" {
  description = "Name prefix for VPC resources"
  type        = string
}

variable "cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.20.0.0/16"
}

variable "azs" {
  description = "Availability zones"
  type        = list(string)
}

variable "single_nat_gateway" {
  description = "Use one NAT gateway (lower cost for non-prod reference stacks)"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags applied to all network resources"
  type        = map(string)
  default     = {}
}
