variable "name"                        { type = string }
variable "vpc_id"                       { type = string }
variable "subnet_ids"                   { type = list(string) }
variable "allowed_security_group_ids"   { type = list(string) }
variable "broker_count"                 { type = number  default = 2 }
variable "broker_instance_type"         { type = string  default = "kafka.t3.small" }
variable "broker_storage_gb"            { type = number  default = 20 }
