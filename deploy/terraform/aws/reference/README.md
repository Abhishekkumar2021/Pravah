# AWS reference stack

Wires Pravah Terraform modules for a single-region staging/production foundation.

## Prerequisites

- Terraform >= 1.5
- AWS CLI configured with permissions for VPC, EKS, RDS, ElastiCache, S3, MSK (if enabled)
- `psql` for post-apply database initialization

## Apply

```bash
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

## Post-apply

1. `aws eks update-kubeconfig --name $(terraform output -raw eks_cluster_name)`
2. `../../../../scripts/deploy/terraform-init-rds.sh`
3. Create Kubernetes secrets (see parent [README.md](../../README.md))
4. Apply Argo CD production Application or `helm upgrade` with `values-prod.yaml`

## MSK

Set `enable_msk = true` in `terraform.tfvars` for managed Kafka. When disabled, point `externalKafka.bootstrapServers` at Confluent Cloud or self-hosted Kafka in Helm values.

## Destroy

```bash
terraform destroy
```

Ensure RDS `deletion_protection` is enabled in production modules before relying on this stack for live data.
