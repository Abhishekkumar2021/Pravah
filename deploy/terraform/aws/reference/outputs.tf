output "eks_cluster_name" {
  description = "EKS cluster name (use with aws eks update-kubeconfig)"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "postgres_host" {
  description = "Helm: externalPostgres.host"
  value       = module.rds.endpoint
}

output "postgres_port" {
  value = module.rds.port
}

output "postgres_username" {
  value = module.rds.username
}

output "postgres_password" {
  description = "Store in Kubernetes secret pravah-postgres-credentials before Helm install"
  value       = random_password.postgres.result
  sensitive   = true
}

output "redis_host" {
  description = "Helm: externalRedis.host"
  value       = module.elasticache.primary_endpoint
}

output "redis_port" {
  value = module.elasticache.port
}

output "redis_password" {
  description = "Store in Kubernetes secret pravah-redis-credentials"
  value       = random_password.redis.result
  sensitive   = true
}

output "kafka_bootstrap_servers" {
  description = "Helm: externalKafka.bootstrapServers (empty when enable_msk=false)"
  value       = var.enable_msk ? module.msk[0].bootstrap_brokers_tls : ""
}

output "artifact_bucket" {
  description = "Helm: externalArtifact.bucket"
  value       = module.artifacts.bucket_name
}

output "artifact_region" {
  description = "Helm: externalArtifact.region"
  value       = module.artifacts.region
}

output "artifacts_irsa_role_arn" {
  description = "Helm: serviceAccount.annotations.eks.amazonaws.com/role-arn"
  value       = module.artifacts_irsa.role_arn
}

output "helm_values_snippet" {
  description = "Example Helm --set flags for values-prod.yaml external services"
  value = trimspace(<<EOT
--set externalPostgres.host=${module.rds.endpoint} \
--set externalRedis.host=${module.elasticache.primary_endpoint} \
--set externalArtifact.bucket=${module.artifacts.bucket_name} \
--set externalArtifact.region=${module.artifacts.region} \
--set externalArtifact.endpoint=https://s3.${var.aws_region}.amazonaws.com \
--set externalArtifact.irsa.enabled=true \
--set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"=${module.artifacts_irsa.role_arn} \
${var.enable_msk ? "--set externalKafka.bootstrapServers=${module.msk[0].bootstrap_brokers_tls}" : "# enable_msk=false — set externalKafka.bootstrapServers to your broker"}
EOT
  )
}

output "post_apply_checklist" {
  value = <<-EOT
    1. aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.aws_region}
    2. ./scripts/deploy/terraform-create-k8s-secrets.sh (or manual — see deploy/terraform/README.md)
    3. ./scripts/deploy/terraform-init-rds.sh (creates per-service databases)
    4. ./scripts/deploy/terraform-helm-bridge.sh (Helm --set flags including IRSA)
    5. Install Argo CD Application or helm upgrade with values-prod.yaml
  EOT
}
