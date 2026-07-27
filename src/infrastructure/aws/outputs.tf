output "backend_ecr_repository_url" {
  description = "ECR repository URL for the backend image."
  value       = aws_ecr_repository.backend.repository_url
}

output "simulator_ecr_repository_url" {
  description = "ECR repository URL for the simulator image."
  value       = aws_ecr_repository.simulator.repository_url
}

output "backend_alb_url" {
  description = "Public backend ALB URL for direct API/debug access."
  value       = "http://${aws_lb.backend.dns_name}"
}

output "frontend_bucket" {
  description = "S3 bucket that should receive the built frontend assets."
  value       = aws_s3_bucket.frontend.bucket
}

output "frontend_cloudfront_url" {
  description = "CloudFront URL for the frontend. /api/* is proxied to the backend ALB."
  value       = "https://${aws_cloudfront_distribution.frontend.domain_name}"
}

output "frontend_cloudfront_distribution_id" {
  description = "CloudFront distribution ID, useful for manual invalidations after frontend uploads."
  value       = aws_cloudfront_distribution.frontend.id
}

output "rds_endpoint" {
  description = "Private RDS PostgreSQL endpoint."
  value       = aws_db_instance.postgres.endpoint
}

output "app_host_public_ip" {
  description = "Public IP of the single EC2 app host when services are deployed."
  value       = try(aws_instance.app_host[0].public_ip, null)
}
