data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

locals {
  project_name             = lookup(local.env, "TF_VAR_project_name", var.project_name)
  environment              = lookup(local.env, "TF_VAR_environment", var.environment)
  name_prefix              = "${local.project_name}-${local.environment}"
  availability_zones       = slice(data.aws_availability_zones.available.names, 0, 2)
  frontend_bucket          = "${local.name_prefix}-frontend-${data.aws_caller_identity.current.account_id}"
  frontend_dist_path       = "${path.module}/../../frontend/dist"
  vpc_cidr                 = lookup(local.env, "TF_VAR_vpc_cidr", var.vpc_cidr)
  api_cidr                 = lookup(local.env, "TF_VAR_api_cidr", var.api_cidr)
  upload_frontend          = try(tobool(lookup(local.env, "TF_VAR_upload_frontend", tostring(var.upload_frontend))), var.upload_frontend)
  deploy_services          = try(tobool(lookup(local.env, "TF_VAR_deploy_services", tostring(var.deploy_services))), var.deploy_services)
  deploy_simulator         = try(tobool(lookup(local.env, "TF_VAR_deploy_simulator", tostring(var.deploy_simulator))), var.deploy_simulator)
  image_tag                = lookup(local.env, "TF_VAR_image_tag", var.image_tag)
  backend_image_override   = var.backend_image != "" ? var.backend_image : lookup(local.env, "TF_VAR_backend_image", "")
  simulator_image_override = var.simulator_image != "" ? var.simulator_image : lookup(local.env, "TF_VAR_simulator_image", "")
  backend_image            = local.backend_image_override != "" ? local.backend_image_override : "${aws_ecr_repository.backend.repository_url}:${local.image_tag}"
  simulator_image          = local.simulator_image_override != "" ? local.simulator_image_override : "${aws_ecr_repository.simulator.repository_url}:${local.image_tag}"
  db_username              = var.db_username != "" ? var.db_username : lookup(local.env, "TF_VAR_db_username", "postgres")
  db_password              = var.db_password != "" ? var.db_password : lookup(local.env, "TF_VAR_db_password", "")
  db_host                  = aws_db_instance.postgres.address
  ignite_dns_name          = "ignite.${aws_service_discovery_private_dns_namespace.internal.name}"
  postgres_engine_version  = lookup(local.env, "TF_VAR_postgres_engine_version", var.postgres_engine_version)
  rds_instance_class       = lookup(local.env, "TF_VAR_rds_instance_class", var.rds_instance_class)
  rds_allocated_storage_gb = tonumber(lookup(local.env, "TF_VAR_rds_allocated_storage_gb", tostring(var.rds_allocated_storage_gb)))
  rds_backup_retention_days = tonumber(lookup(
    local.env,
    "TF_VAR_rds_backup_retention_days",
    tostring(var.rds_backup_retention_days)
  ))
  rds_deletion_protection = try(tobool(lookup(local.env, "TF_VAR_rds_deletion_protection", tostring(var.rds_deletion_protection))), var.rds_deletion_protection)
  rds_skip_final_snapshot = try(tobool(lookup(local.env, "TF_VAR_rds_skip_final_snapshot", tostring(var.rds_skip_final_snapshot))), var.rds_skip_final_snapshot)
  ec2_instance_type       = lookup(local.env, "TF_VAR_ec2_instance_type", var.ec2_instance_type)
  ec2_volume_gb           = tonumber(lookup(local.env, "TF_VAR_ec2_volume_gb", tostring(var.ec2_volume_gb)))
  backend_cpu             = tonumber(lookup(local.env, "TF_VAR_backend_cpu", tostring(var.backend_cpu)))
  backend_memory          = tonumber(lookup(local.env, "TF_VAR_backend_memory", tostring(var.backend_memory)))
  backend_desired_count   = tonumber(lookup(local.env, "TF_VAR_backend_desired_count", tostring(var.backend_desired_count)))
  simulator_cpu           = tonumber(lookup(local.env, "TF_VAR_simulator_cpu", tostring(var.simulator_cpu)))
  simulator_memory        = tonumber(lookup(local.env, "TF_VAR_simulator_memory", tostring(var.simulator_memory)))
  ignite_cpu              = tonumber(lookup(local.env, "TF_VAR_ignite_cpu", tostring(var.ignite_cpu)))
  ignite_memory           = tonumber(lookup(local.env, "TF_VAR_ignite_memory", tostring(var.ignite_memory)))
  log_retention_days      = tonumber(lookup(local.env, "TF_VAR_log_retention_days", tostring(var.log_retention_days)))
  frontend_files          = local.upload_frontend ? fileset(local.frontend_dist_path, "**/*") : []
  mime_types = {
    ".css"   = "text/css"
    ".html"  = "text/html"
    ".ico"   = "image/x-icon"
    ".js"    = "application/javascript"
    ".json"  = "application/json"
    ".png"   = "image/png"
    ".svg"   = "image/svg+xml"
    ".txt"   = "text/plain"
    ".webp"  = "image/webp"
    ".woff"  = "font/woff"
    ".woff2" = "font/woff2"
  }
}

resource "aws_vpc" "main" {
  cidr_block           = local.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(local.vpc_cidr, 8, count.index + 1)
  availability_zone       = local.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${count.index + 1}"
  }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(local.vpc_cidr, 8, count.index + 11)
  availability_zone = local.availability_zones[count.index]

  tags = {
    Name = "${local.name_prefix}-private-${count.index + 1}"
  }
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-nat"
  }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "${local.name_prefix}-nat"
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${local.name_prefix}-public-rt"
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = {
    Name = "${local.name_prefix}-private-rt"
  }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb"
  description = "Public HTTP access to WattSmart backend ALB."
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [local.api_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-alb-sg"
  }
}

resource "aws_security_group" "ecs" {
  name        = "${local.name_prefix}-ecs"
  description = "ECS service-to-service and egress access."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Backend HTTP from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-app-host-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds"
  description = "PostgreSQL access from ECS tasks."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = {
    Name = "${local.name_prefix}-rds-sg"
  }
}

resource "aws_ecr_repository" "backend" {
  name                 = "${local.name_prefix}-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "simulator" {
  name                 = "${local.name_prefix}-simulator"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_db_subnet_group" "postgres" {
  name       = "${local.name_prefix}-postgres"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "postgres" {
  identifier              = "${local.name_prefix}-postgres"
  engine                  = "postgres"
  engine_version          = local.postgres_engine_version
  instance_class          = local.rds_instance_class
  allocated_storage       = local.rds_allocated_storage_gb
  db_name                 = "wattsmart"
  username                = local.db_username
  password                = local.db_password
  db_subnet_group_name    = aws_db_subnet_group.postgres.name
  vpc_security_group_ids  = [aws_security_group.rds.id]
  publicly_accessible     = false
  skip_final_snapshot     = local.rds_skip_final_snapshot
  deletion_protection     = local.rds_deletion_protection
  backup_retention_period = local.rds_backup_retention_days

  lifecycle {
    precondition {
      condition     = local.db_password != ""
      error_message = "Set TF_VAR_db_password in the repo .env file before applying the AWS infrastructure."
    }
  }
}

resource "aws_secretsmanager_secret" "app" {
  name = "${local.name_prefix}/app-env"
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    DB_PASSWORD    = local.db_password
    DB_USERNAME    = local.db_username
    GEMINI_API_KEY = lookup(local.env, "GEMINI_API_KEY", "")
    RESEND_API_KEY = lookup(local.env, "RESEND_API_KEY", "")
  })
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${local.name_prefix}/backend"
  retention_in_days = local.log_retention_days
}

resource "aws_cloudwatch_log_group" "simulator" {
  name              = "/ecs/${local.name_prefix}/simulator"
  retention_in_days = local.log_retention_days
}

resource "aws_cloudwatch_log_group" "ignite" {
  name              = "/ecs/${local.name_prefix}/ignite"
  retention_in_days = local.log_retention_days
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "${local.name_prefix}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name = "${local.name_prefix}-ecs-secrets"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue"
      ]
      Resource = aws_secretsmanager_secret.app.arn
    }]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "${local.name_prefix}-ecs-task"

  assume_role_policy = aws_iam_role.ecs_task_execution.assume_role_policy
}

resource "aws_iam_role" "app_host" {
  name = "${local.name_prefix}-app-host"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "app_host_ecr" {
  role       = aws_iam_role.app_host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "app_host_ssm" {
  role       = aws_iam_role.app_host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "app_host_secrets" {
  name = "${local.name_prefix}-app-host-secrets"
  role = aws_iam_role.app_host.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue"
      ]
      Resource = aws_secretsmanager_secret.app.arn
    }]
  })
}

resource "aws_iam_instance_profile" "app_host" {
  name = "${local.name_prefix}-app-host"
  role = aws_iam_role.app_host.name
}

resource "aws_service_discovery_private_dns_namespace" "internal" {
  name = "${local.name_prefix}.local"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "ignite" {
  name = "ignite"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.internal.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }
}

resource "aws_lb" "backend" {
  name               = "${local.name_prefix}-backend"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "backend" {
  name_prefix = "ws-${substr(local.environment, 0, 2)}-"
  port        = 8080
  protocol    = "HTTP"
  target_type = "instance"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    path                = "/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_listener" "backend_http" {
  load_balancer_arn = aws_lb.backend.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

resource "aws_instance" "app_host" {
  count                       = local.deploy_services ? 1 : 0
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = local.ec2_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.ecs.id]
  iam_instance_profile        = aws_iam_instance_profile.app_host.name
  associate_public_ip_address = true

  root_block_device {
    volume_size = local.ec2_volume_gb
    volume_type = "gp3"
  }

  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/templates/app-host-user-data.sh.tftpl", {
    aws_region       = var.aws_region != "" ? var.aws_region : lookup(local.env, "AWS_REGION", "eu-central-1")
    backend_image    = local.backend_image
    simulator_image  = local.simulator_image
    db_url           = "jdbc:postgresql://${local.db_host}:5432/wattsmart"
    db_username      = local.db_username
    secret_id        = aws_secretsmanager_secret.app.id
    deploy_simulator = local.deploy_simulator ? "true" : "false"
    email_provider   = lookup(local.env, "EMAIL_PROVIDER", "logging")
    email_from       = lookup(local.env, "EMAIL_FROM", "")
    gemini_model     = lookup(local.env, "GEMINI_MODEL", "gemini-2.0-flash")
    frontend_origin  = "https://${aws_cloudfront_distribution.frontend.domain_name}"
  })

  depends_on = [
    aws_db_instance.postgres,
    aws_ecr_repository.backend,
    aws_ecr_repository.simulator,
    aws_iam_role_policy.app_host_secrets,
    aws_iam_role_policy_attachment.app_host_ecr
  ]

  tags = {
    Name = "${local.name_prefix}-app-host"
  }
}

resource "aws_lb_target_group_attachment" "backend_app_host" {
  count            = local.deploy_services ? 1 : 0
  target_group_arn = aws_lb_target_group.backend.arn
  target_id        = aws_instance.app_host[0].id
  port             = 8080
}

resource "aws_ecs_task_definition" "ignite" {
  count                    = 0
  family                   = "${local.name_prefix}-ignite"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = local.ignite_cpu
  memory                   = local.ignite_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "ignite"
    image     = "apacheignite/ignite:2.16.0"
    essential = true
    portMappings = [{
      containerPort = 10800
      protocol      = "tcp"
    }]
    environment = [
      { name = "JVM_OPTS", value = "-Xms512m -Xmx512m" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.ignite.name
        awslogs-region        = var.aws_region != "" ? var.aws_region : lookup(local.env, "AWS_REGION", "eu-central-1")
        awslogs-stream-prefix = "ignite"
      }
    }
  }])
}

resource "aws_ecs_service" "ignite" {
  count           = 0
  name            = "${local.name_prefix}-ignite"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ignite[0].arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn   = aws_service_discovery_service.ignite.arn
    container_name = "ignite"
    container_port = 10800
  }
}

resource "aws_ecs_task_definition" "backend" {
  count                    = 0
  family                   = "${local.name_prefix}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = local.backend_cpu
  memory                   = local.backend_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "backend"
    image     = local.backend_image
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "core" },
      { name = "SERVER_PORT", value = "8080" },
      { name = "DB_URL", value = "jdbc:postgresql://${local.db_host}:5432/wattsmart" },
      { name = "DB_USERNAME", value = local.db_username },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = "kafka:9092" },
      { name = "IGNITE_HOST", value = local.ignite_dns_name },
      { name = "IGNITE_PORT", value = "10800" },
      { name = "EMAIL_PROVIDER", value = lookup(local.env, "EMAIL_PROVIDER", "logging") },
      { name = "EMAIL_FROM", value = lookup(local.env, "EMAIL_FROM", "") },
      { name = "GEMINI_MODEL", value = lookup(local.env, "GEMINI_MODEL", "gemini-2.0-flash") }
    ]
    secrets = [
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_PASSWORD::" },
      { name = "GEMINI_API_KEY", valueFrom = "${aws_secretsmanager_secret.app.arn}:GEMINI_API_KEY::" },
      { name = "RESEND_API_KEY", valueFrom = "${aws_secretsmanager_secret.app.arn}:RESEND_API_KEY::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.backend.name
        awslogs-region        = var.aws_region != "" ? var.aws_region : lookup(local.env, "AWS_REGION", "eu-central-1")
        awslogs-stream-prefix = "backend"
      }
    }
  }])
}

resource "aws_ecs_service" "backend" {
  count           = 0
  name            = "${local.name_prefix}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend[0].arn
  desired_count   = local.backend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  depends_on = [
    aws_ecs_service.ignite,
    aws_lb_listener.backend_http
  ]
}

resource "aws_ecs_task_definition" "simulator" {
  count                    = 0
  family                   = "${local.name_prefix}-simulator"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = local.simulator_cpu
  memory                   = local.simulator_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "simulator"
    image     = local.simulator_image
    essential = true
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "simulator" },
      { name = "FLYWAY_ENABLED", value = "false" },
      { name = "DB_URL", value = "jdbc:postgresql://${local.db_host}:5432/wattsmart" },
      { name = "DB_USERNAME", value = local.db_username },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = "kafka:9092" },
      { name = "IGNITE_HOST", value = local.ignite_dns_name },
      { name = "IGNITE_PORT", value = "10800" }
    ]
    secrets = [
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_PASSWORD::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.simulator.name
        awslogs-region        = var.aws_region != "" ? var.aws_region : lookup(local.env, "AWS_REGION", "eu-central-1")
        awslogs-stream-prefix = "simulator"
      }
    }
  }])
}

resource "aws_ecs_service" "simulator" {
  count           = 0
  name            = "${local.name_prefix}-simulator"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.simulator[0].arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  depends_on = [
    aws_ecs_service.backend
  ]
}

resource "aws_s3_bucket" "frontend" {
  bucket = local.frontend_bucket

  tags = {
    Name = "${local.name_prefix}-frontend"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${local.name_prefix}-frontend-oac"
  description                       = "WattSmart frontend S3 access"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "frontend-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    domain_name = aws_lb.backend.dns_name
    origin_id   = "backend-alb"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "backend-alb"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    compress               = true

    forwarded_values {
      query_string = true
      headers      = ["Authorization", "Content-Type"]

      cookies {
        forward = "all"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern           = "/swagger-ui/*"
    target_origin_id       = "backend-alb"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    compress               = true

    forwarded_values {
      query_string = true

      cookies {
        forward = "none"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern           = "/api-docs/*"
    target_origin_id       = "backend-alb"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    compress               = true

    forwarded_values {
      query_string = true

      cookies {
        forward = "none"
      }
    }
  }

  default_cache_behavior {
    target_origin_id       = "frontend-s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${local.name_prefix}-frontend"
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.frontend.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.frontend.arn
        }
      }
    }]
  })
}

resource "aws_s3_object" "frontend_assets" {
  for_each = toset(local.frontend_files)

  bucket       = aws_s3_bucket.frontend.id
  key          = each.value
  source       = "${local.frontend_dist_path}/${each.value}"
  etag         = filemd5("${local.frontend_dist_path}/${each.value}")
  content_type = lookup(local.mime_types, try(regex("\\.[^.]+$", each.value), ""), "application/octet-stream")
}
