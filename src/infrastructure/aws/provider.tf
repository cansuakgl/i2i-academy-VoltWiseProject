locals {
  root_env_path = "${path.module}/../../../.env"
  raw_env_lines = fileexists(local.root_env_path) ? split("\n", file(local.root_env_path)) : []
  env_grouped = {
    for line in local.raw_env_lines :
    trimspace(split("=", line)[0]) => trimspace(join("=", slice(split("=", line), 1, length(split("=", line)))))...
    if length(split("=", line)) > 1 && !startswith(trimspace(line), "#") && trimspace(line) != ""
  }
  env = {
    for key, values in local.env_grouped :
    key => values[length(values) - 1]
  }
}

provider "aws" {
  region     = var.aws_region != "" ? var.aws_region : lookup(local.env, "AWS_REGION", "eu-central-1")
  access_key = var.aws_access_key_id != "" ? var.aws_access_key_id : lookup(local.env, "AWS_ACCESS_KEY_ID", null)
  secret_key = var.aws_secret_access_key != "" ? var.aws_secret_access_key : lookup(local.env, "AWS_SECRET_ACCESS_KEY", null)
}
