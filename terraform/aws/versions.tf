terraform {
  required_version = ">= 1.11"
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "> 6"
      configuration_aliases = [aws.us-east-1]
    }
    random = {
      source  = "hashicorp/random"
      version = "> 3.7.1"
    }
  }
}

