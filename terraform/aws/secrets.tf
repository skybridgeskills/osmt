
resource "random_id" "jwt_secret" {
  byte_length = 32
  keepers = {
    regenerate_on = "initial"
  }
}

# Generate random password for single-auth admin when not provided
resource "random_password" "single_auth_admin" {
  count   = var.single_auth.admin_password == null ? 1 : 0
  length  = 16
  special = true
  keepers = {
    regenerate_on = "initial"
  }
}

