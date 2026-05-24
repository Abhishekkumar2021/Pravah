output "role_arn" {
  description = "Helm: serviceAccount.annotations.eks.amazonaws.com/role-arn"
  value       = aws_iam_role.this.arn
}
