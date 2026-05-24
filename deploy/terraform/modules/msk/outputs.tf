output "bootstrap_brokers_tls" {
  value = aws_msk_cluster.this.bootstrap_brokers_tls
}

output "cluster_arn" {
  value = aws_msk_cluster.this.arn
}

output "security_group_id" {
  value = aws_security_group.this.id
}
