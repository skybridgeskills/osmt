resource "random_id" "tenant_admin_password" {
  byte_length = 8
  keepers = {
    regenerate_on = "initial"
  }
}

resource "random_id" "jwt_secret" {
  byte_length = 32
  keepers = {
    regenerate_on = "initial"
  }
}

