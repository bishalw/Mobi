variable "aws_region"   { type = string  default = "us-east-1" }
variable "db_username"  { type = string  default = "mobi_admin" }
variable "db_password"  { type = string  sensitive = true }
