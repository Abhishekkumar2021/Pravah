{{/*
Production install guardrails (active when requireProductionSecrets=true).
*/}}
{{- define "pravah.productionValidate" -}}
{{- if .Values.requireProductionSecrets }}
{{- if and (not .Values.pravah.internalServiceExistingSecret) (not .Values.pravah.internalServiceSecret) }}
{{- fail "pravah.internalServiceSecret must be set, or set pravah.internalServiceExistingSecret to an existing Secret name" }}
{{- end }}
{{- if and (not .Values.postgres.enabled) (not .Values.externalPostgres.host) (not .Values.externalPostgres.existingSecret) }}
{{- fail "externalPostgres.host or externalPostgres.existingSecret is required when postgres.enabled is false" }}
{{- end }}
{{- if and (not .Values.kafka.enabled) (not .Values.externalKafka.bootstrapServers) }}
{{- fail "externalKafka.bootstrapServers is required when kafka.enabled is false" }}
{{- end }}
{{- if and (not .Values.redis.enabled) (not .Values.externalRedis.host) }}
{{- fail "externalRedis.host is required when redis.enabled is false" }}
{{- end }}
{{- if and (not .Values.minio.enabled) (not .Values.externalArtifact.endpoint) (not .Values.externalArtifact.existingSecret) (not .Values.externalArtifact.irsa.enabled) }}
{{- fail "externalArtifact.endpoint, externalArtifact.existingSecret, or externalArtifact.irsa.enabled is required when minio.enabled is false" }}
{{- end }}
{{- if .Values.minio.enabled }}
{{- fail "minio.enabled must be false in production; use externalArtifact (S3)" }}
{{- end }}
{{- if .Values.mailhog.enabled }}
{{- fail "mailhog.enabled must be false in production; configure smtp.*" }}
{{- end }}
{{- if and (not .Values.smtp.host) (not .Values.mailhog.enabled) }}
{{- fail "smtp.host is required in production (tenant password reset + notification alerts)" }}
{{- end }}
{{- if not .Values.pravah.wsAllowedOrigins }}
{{- fail "pravah.wsAllowedOrigins is required in production (WebSocket CORS)" }}
{{- end }}
{{- if not .Values.pravah.frontendBaseUrl }}
{{- fail "pravah.frontendBaseUrl is required in production (email links)" }}
{{- end }}
{{- if not .Values.pravah.hooksBaseUrl }}
{{- fail "pravah.hooksBaseUrl is required in production (public webhook URLs)" }}
{{- end }}
{{- if or (eq .Values.image.tag "latest") (eq .Values.image.tag "") }}
{{- fail "image.tag must be a pinned git SHA or release tag in production (not latest/empty)" }}
{{- end }}
{{- if and (not .Values.pravah.runnerBootstrapExistingSecret) (not .Values.pravah.runnerBootstrapSecret) }}
{{- fail "pravah.runnerBootstrapExistingSecret or pravah.runnerBootstrapSecret is required in production" }}
{{- end }}
{{- if eq .Values.pravah.rateLimitFailOpen "true" }}
{{- fail "pravah.rateLimitFailOpen must be false in production" }}
{{- end }}
{{- if ne .Values.pravah.authRefreshCookieSecure "true" }}
{{- fail "pravah.authRefreshCookieSecure must be true in production (HTTPS)" }}
{{- end }}
{{- if .Values.vault.enabled }}
{{- fail "vault.enabled must be false in production; configure externalVault.address with Kubernetes auth" }}
{{- end }}
{{- $runner := index .Values.services "runner-service" }}
{{- if and $runner.enabled $runner.exposeGrpc.enabled (not .Values.runnerGrpcTls.enabled) }}
{{- fail "runnerGrpcTls.enabled must be true when runner-service.exposeGrpc.enabled (external runner gRPC)" }}
{{- end }}
{{- if and .Values.runnerGrpcTls.enabled (not (include "pravah.runnerGrpcTls.secretName" .)) }}
{{- fail "runnerGrpcTls.existingSecret or certManager.runnerGrpc.secretName is required when runnerGrpcTls.enabled" }}
{{- end }}
{{- if and .Values.runnerGrpcTls.enabled .Values.certManager.runnerGrpc.enabled (not .Values.certManager.runnerGrpc.issuerName) }}
{{- fail "certManager.runnerGrpc.issuerName is required when certManager.runnerGrpc.enabled" }}
{{- end }}
{{- if and .Values.runnerPki.enabled (not .Values.externalVault.address) (not .Values.vault.enabled) }}
{{- fail "runnerPki.enabled requires vault.enabled (local) or externalVault.address (production)" }}
{{- end }}
{{- if and .Values.runnerPki.enabled (not .Values.runnerGrpcTls.enabled) }}
{{- fail "runnerPki.enabled requires runnerGrpcTls.enabled (server mTLS)" }}
{{- end }}
{{- if and .Values.runnerPki.enabled (not $runner.needsVault) }}
{{- fail "services.runner-service.needsVault must be true when runnerPki.enabled" }}
{{- end }}
{{- if and .Values.runnerGrpcTls.enabled (not .Values.runnerGrpcTls.existingSecret) (not .Values.certManager.runnerGrpc.enabled) }}
{{- fail "runnerGrpcTls.existingSecret is required when runnerGrpcTls.enabled (or enable certManager.runnerGrpc)" }}
{{- end }}
{{- if and .Values.runnerGrpcTls.enabled .Values.runnerGrpcTls.requireClientAuth (not .Values.runnerGrpcTls.clientCaKey) }}
{{- fail "runnerGrpcTls.clientCaKey is required when runnerGrpcTls.requireClientAuth is true" }}
{{- end }}
{{- range $key, $svc := .Values.services }}
{{- if and $svc.enabled $svc.needsVault (not $.Values.externalVault.address) }}
{{- fail (printf "services.%s.needsVault=true requires externalVault.address in production" $key) }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}
