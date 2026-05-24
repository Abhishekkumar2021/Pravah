# Terraform — Cloud infrastructure (pathway #10)

Terraform modules provision the **managed services** that `values-prod.yaml` expects: VPC, EKS, RDS PostgreSQL, ElastiCache Redis, S3 artifacts, and optional Amazon MSK. Helm + Argo CD (pathway #9) deploy the Pravah platform **on top** of this infrastructure per [ADR-010](../../docs/adr/ADR-010-kubernetes-helm-argocd.md).

## Layout

| Path | Purpose |
|------|---------|
| `modules/network` | VPC, public/private subnets, NAT |
| `modules/eks` | EKS cluster + managed node group |
| `modules/rds` | PostgreSQL 16 (multi-AZ) |
| `modules/elasticache` | Redis 7 replication group (TLS + auth) |
| `modules/s3` | Encrypted artifacts bucket |
| `modules/msk` | Optional Amazon MSK (Kafka) |
| `aws/reference` | Reference stack wiring all modules |

GCP/Azure modules are planned (US-09.15); AWS is the first supported cloud.

## Quick start (AWS reference)

```bash
cd deploy/terraform/aws/reference
cp terraform.tfvars.example terraform.tfvars
# Edit artifacts_bucket_name (globally unique)

terraform init
terraform plan
terraform apply

aws eks update-kubeconfig --name "$(terraform output -raw eks_cluster_name)" --region us-east-1

# Create per-service databases on RDS
../../../../scripts/deploy/terraform-init-rds.sh

# Create Kubernetes secrets (passwords from terraform output -raw)
kubectl create namespace pravah
kubectl -n pravah create secret generic pravah-postgres-credentials \
  --from-literal=username="$(terraform output -raw postgres_username)" \
  --from-literal=password="$(terraform output -raw postgres_password)"
kubectl -n pravah create secret generic pravah-redis-credentials \
  --from-literal=password="$(terraform output -raw redis_password)"

# Helm / Argo CD with external endpoints
terraform output -raw helm_values_snippet
# See deploy/README.md Production Deployment
```

Validate locally (no AWS creds):

```bash
make validate-terraform
```

## Helm mapping

| Terraform output | Helm value |
|------------------|------------|
| `postgres_host` | `externalPostgres.host` |
| `redis_host` | `externalRedis.host` |
| `kafka_bootstrap_servers` | `externalKafka.bootstrapServers` (when MSK enabled) |
| `artifact_bucket` | `externalArtifact.bucket` |
| `artifact_region` | `externalArtifact.region` |

Vault, SMTP, and GHCR pull secrets are operator-managed (not created by this stack).

## Cost estimate (AWS reference, us-east-1, 2026)

Approximate monthly cost for **staging** defaults (`enable_msk=false`, `single_nat_gateway=true`, 2× `m6i.large` nodes):

| Resource | ~USD/month |
|----------|------------|
| EKS control plane | $73 |
| EC2 nodes (2× m6i.large) | $140 |
| RDS db.r6g.large Multi-AZ | $290 |
| ElastiCache cache.r6g.large ×2 | $260 |
| NAT gateway | $35 |
| S3 + data transfer | $20–50 |
| **Total (no MSK)** | **~$820–850** |

With `enable_msk=true` (3× kafka.m5.large): add **~$450+/month**.

Use `single_nat_gateway=true` and smaller instance types only in non-production environments.

## CI

`.github/workflows/terraform-ci.yml` runs `terraform fmt -check`, `init -backend=false`, and `validate` on PRs touching `deploy/terraform/**`.

## State

Configure a remote backend (S3 + DynamoDB) before production use. The reference stack uses local state by default for evaluation only.

Example backend block (add to `aws/reference/versions.tf`):

```hcl
terraform {
  backend "s3" {
    bucket         = "pravah-terraform-state"
    key            = "aws/reference/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "pravah-terraform-locks"
    encrypt        = true
  }
}
```
