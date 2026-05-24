variable "name" {
  type = string
}

variable "bucket_name" {
  description = "Globally unique S3 bucket name for execution artifacts"
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
