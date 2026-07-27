variable "project_name" {
  type        = string
  description = "Short project name used in AWS resource names."
  default     = "wattsmart"
}

variable "environment" {
  type        = string
  description = "Deployment environment label."
  default     = "dev"
}

variable "aws_region" {
  type        = string
  description = "AWS region. Can also be supplied by AWS_REGION in the repo .env file."
  default     = ""
}

variable "aws_access_key_id" {
  type        = string
  description = "Optional AWS access key. Prefer AWS_ACCESS_KEY_ID in local .env or shell."
  sensitive   = true
  default     = ""
}

variable "aws_secret_access_key" {
  type        = string
  description = "Optional AWS secret key. Prefer AWS_SECRET_ACCESS_KEY in local .env or shell."
  sensitive   = true
  default     = ""
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the WattSmart VPC."
  default     = "10.42.0.0/16"
}

variable "api_cidr" {
  type        = string
  description = "CIDR allowed to access the public backend ALB. Use 0.0.0.0/0 for a public demo."
  default     = "0.0.0.0/0"
}

variable "deploy_services" {
  type        = bool
  description = "Set false for the first Terraform apply to create ECR and managed infrastructure before images are pushed."
  default     = false
}

variable "upload_frontend" {
  type        = bool
  description = "Upload src/frontend/dist assets to the Terraform-created S3 frontend bucket."
  default     = false
}

variable "deploy_simulator" {
  type        = bool
  description = "Whether to run the telemetry simulator ECS service."
  default     = true
}

variable "image_tag" {
  type        = string
  description = "Image tag used when backend_image/simulator_image are not supplied."
  default     = "latest"
}

variable "backend_image" {
  type        = string
  description = "Optional full backend container image URI. Defaults to the Terraform-created backend ECR repository and image_tag."
  default     = ""
}

variable "simulator_image" {
  type        = string
  description = "Optional full simulator container image URI. Defaults to the Terraform-created simulator ECR repository and image_tag."
  default     = ""
}

variable "db_username" {
  type        = string
  description = "RDS PostgreSQL username."
  default     = ""
}

variable "db_password" {
  type        = string
  description = "RDS PostgreSQL password."
  sensitive   = true
  default     = ""
}

variable "postgres_engine_version" {
  type        = string
  description = "RDS PostgreSQL engine version."
  default     = "16.3"
}

variable "rds_instance_class" {
  type        = string
  description = "RDS PostgreSQL instance class."
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage_gb" {
  type        = number
  description = "RDS allocated storage in GB."
  default     = 20
}

variable "rds_backup_retention_days" {
  type        = number
  description = "RDS backup retention period in days."
  default     = 1
}

variable "rds_deletion_protection" {
  type        = bool
  description = "Enable deletion protection for RDS."
  default     = false
}

variable "rds_skip_final_snapshot" {
  type        = bool
  description = "Skip final snapshot on RDS deletion. Keep true for disposable demos."
  default     = true
}

variable "ec2_instance_type" {
  type        = string
  description = "Single EC2 instance type that runs backend, simulator, Kafka, and Ignite containers."
  default     = "t3.small"
}

variable "ec2_volume_gb" {
  type        = number
  description = "Root EBS volume size for the single EC2 app host."
  default     = 30
}

variable "backend_cpu" {
  type        = number
  description = "Backend Fargate CPU units."
  default     = 1024
}

variable "backend_memory" {
  type        = number
  description = "Backend Fargate memory MB."
  default     = 2048
}

variable "backend_desired_count" {
  type        = number
  description = "Backend ECS desired task count."
  default     = 1
}

variable "simulator_cpu" {
  type        = number
  description = "Simulator Fargate CPU units."
  default     = 512
}

variable "simulator_memory" {
  type        = number
  description = "Simulator Fargate memory MB."
  default     = 1024
}

variable "ignite_cpu" {
  type        = number
  description = "Ignite Fargate CPU units."
  default     = 512
}

variable "ignite_memory" {
  type        = number
  description = "Ignite Fargate memory MB."
  default     = 1024
}

variable "log_retention_days" {
  type        = number
  description = "CloudWatch log retention in days."
  default     = 14
}
