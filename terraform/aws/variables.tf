variable "ecr_registry" {
  type        = string
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
    # rds        = {}
    # opensearch = {}
    # vpc        = {}
  }
  description = "Configuration options for the OSMT services (e.g. email from address)"
}


variable "auth_mode" {
  type        = string
  default     = "single-auth"
  description = "Authentication mode: 'single-auth' for simple admin/password auth, 'oauth2' for OAuth2/Okta authentication"

  validation {
    condition     = contains(["single-auth", "oauth2"], var.auth_mode)
    error_message = "auth_mode must be either 'single-auth' or 'oauth2'"
  }
}

variable "single_auth" {
  type = object({
    admin_username = optional(string, "admin")
    admin_password = optional(string, null)
  })
  default = {
    admin_username = "admin"
    admin_password = null
  }
  description = "Single authentication mode configuration. If admin_password is not provided, a random password will be generated."
  sensitive   = true
}

variable "oauth2" {
  type = object({
    issuer       = optional(string, null)
    client_id    = optional(string, null)
    client_secret = optional(string, null)
    audience     = optional(string, null)
  })
  default = {
    issuer       = null
    client_id    = null
    client_secret = null
    audience     = null
  }
  description = "OAuth2/Okta authentication configuration"
  sensitive   = true
}

