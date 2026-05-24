variable "name" {
  type = string
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_provider_url" {
  type = string
}

variable "namespace" {
  description = "Kubernetes namespace for the Pravah service account"
  type        = string
  default     = "pravah"
}

variable "service_account_name" {
  description = "Kubernetes service account name (Helm fullnameOverride, default pravah)"
  type        = string
  default     = "pravah"
}

variable "bucket_arn" {
  type = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
