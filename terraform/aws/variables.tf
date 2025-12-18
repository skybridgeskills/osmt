variable "ecr_registry" {
  type        = string
  default     = "853091924495.dkr.ecr.us-west-2.amazonaws.com"
  description = "ECR registry URL for Docker images"
}

variable "config" {
  type = object({
    app = object({
      from_address = optional(string, null)
    })
    env = object({
      name = string
      tld  = string
    })
    cache = optional(object({
      name                 = optional(string, null)
      node_type            = optional(string, "cache.t3.micro")
      number_of_nodes      = optional(number, null)
      parameter_group_name = optional(string, "default.redis7")
    }), {})
    cloudfront = optional(object({
      alternate_domains      = optional(list(string), [])
      alternate_acm_cert_arn = optional(string, null)
    }), {})
    tenant_home = optional(object({
      alternate_domains = optional(list(string), [])
    }), {})
    ops = optional(object({
      allowed_cidrs = optional(list(string), ["0.0.0.0/0"])
    }), {})
    rds = optional(object({
      allocated_storage           = optional(number, 20)
      name                        = optional(string, null)
      db_name                     = optional(string, "osmt_db")
      db_user                     = optional(string, "osmt")
      deletion_protection         = optional(bool, false)
      initial_engine_version      = optional(string, "8.0")
      instance_class              = optional(string, "db.t3.micro")
      max_allocated_storage       = optional(number, 100)
      restore_snapshot_identifier = optional(string, null)
    }), {})
    opensearch = optional(object({
      instance_type         = optional(string, "t3.small.search")
      instance_count        = optional(number, 1)
      volume_size           = optional(number, 10)
      dedicated_master_type = optional(string, null)
      master_instance_count = optional(number, 0)
    }), {})
    tags = optional(map(string), {})
    vpc = optional(object({
      name = optional(string, null)
      cidr = optional(string, "10.0.0.0/18")
    }), {})
    sendgrid = optional(object({
      domains = list(object({
        domain = string
        records = list(object({
          type  = string
          host  = string
          value = string
        }))
      }))
      }), {
      domains = []
    })
  })
  default = {
    app = {
      from_address = null
    }
    env = {
      name = "staging"
      tld  = "prettygoodskills.com"
    }
    # cache      = {}
    # cloudfront = {}
    # ops        = {}
    # rds        = {}
    # opensearch = {}
    # vpc        = {}
  }
  description = "Configuration options for the OSMT services (e.g. email from address)"
}

variable "sendgrid_api_key" {
  type        = string
  description = "API key for sending emails"
  sensitive   = true
}

variable "tenant_admin_password" {
  type        = string
  description = "Password for tenant admin"
  sensitive   = true
}

