{{/*
Expand the name of the chart.
*/}}
{{- define "pravah.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "pravah.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "pravah.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "pravah.runnerGrpcTls.secretName" -}}
{{- if .Values.certManager.runnerGrpc.enabled -}}
{{- .Values.certManager.runnerGrpc.secretName | default .Values.runnerGrpcTls.existingSecret -}}
{{- else -}}
{{- .Values.runnerGrpcTls.existingSecret -}}
{{- end -}}
{{- end }}

{{- define "pravah.labels" -}}
helm.sh/chart: {{ include "pravah.chart" . }}
{{ include "pravah.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "pravah.selectorLabels" -}}
app.kubernetes.io/name: {{ include "pravah.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "pravah.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "pravah.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Postgres hostname - bundled or external (backend; use pravah.db.* when PgBouncer is enabled)
*/}}
{{- define "pravah.postgres.hostname" -}}
{{- if .Values.postgres.enabled }}
{{- printf "%s-postgres" (include "pravah.fullname" .) }}
{{- else }}
{{- .Values.externalPostgres.host }}
{{- end }}
{{- end }}

{{- define "pravah.postgres.port" -}}
{{- if .Values.postgres.enabled }}
{{- 5432 }}
{{- else }}
{{- .Values.externalPostgres.port | default 5432 }}
{{- end }}
{{- end }}

{{/*
Application database endpoint (PgBouncer when enabled, else PostgreSQL/RDS).
*/}}
{{- define "pravah.db.hostname" -}}
{{- if .Values.pgbouncer.enabled }}
{{- printf "%s-pgbouncer" (include "pravah.fullname" .) }}
{{- else }}
{{- include "pravah.postgres.hostname" . }}
{{- end }}
{{- end }}

{{- define "pravah.db.port" -}}
{{- if .Values.pgbouncer.enabled }}
{{- .Values.pgbouncer.port | default 6432 }}
{{- else }}
{{- include "pravah.postgres.port" . }}
{{- end }}
{{- end }}

{{- define "pravah.pgbouncer.datasourceUrl" -}}
{{- $root := index . 0 -}}
{{- $dbName := index . 1 -}}
{{- if $root.Values.pgbouncer.enabled -}}
{{- printf "jdbc:postgresql://%s:%v/%s?prepareThreshold=0" (include "pravah.db.hostname" $root) (include "pravah.db.port" $root) $dbName -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:%v/%s" (include "pravah.db.hostname" $root) (include "pravah.db.port" $root) $dbName -}}
{{- end }}
{{- end }}

{{/*
Kafka hostname - bundled or external
*/}}
{{- define "pravah.kafka.hostname" -}}
{{- if .Values.kafka.enabled }}
{{- printf "%s-kafka" (include "pravah.fullname" .) }}
{{- end }}
{{- end }}

{{- define "pravah.kafka.bootstrapServers" -}}
{{- if .Values.kafka.enabled }}
{{- printf "%s:9092" (include "pravah.kafka.hostname" .) }}
{{- else }}
{{- .Values.externalKafka.bootstrapServers }}
{{- end }}
{{- end }}

{{/*
Redis hostname - bundled or external
*/}}
{{- define "pravah.redis.hostname" -}}
{{- if .Values.redis.enabled }}
{{- printf "%s-redis" (include "pravah.fullname" .) }}
{{- else }}
{{- .Values.externalRedis.host }}
{{- end }}
{{- end }}

{{- define "pravah.redis.port" -}}
{{- if .Values.redis.enabled }}
{{- 6379 }}
{{- else }}
{{- .Values.externalRedis.port | default 6379 }}
{{- end }}
{{- end }}

{{/*
MinIO / artifact storage hostname
*/}}
{{- define "pravah.minio.hostname" -}}
{{- if .Values.minio.enabled }}
{{- printf "%s-minio" (include "pravah.fullname" .) }}
{{- end }}
{{- end }}

{{- define "pravah.artifact.endpoint" -}}
{{- if .Values.minio.enabled }}
{{- printf "http://%s:9000" (include "pravah.minio.hostname" .) }}
{{- else }}
{{- required "externalArtifact.endpoint is required when minio.enabled is false" .Values.externalArtifact.endpoint }}
{{- end }}
{{- end }}

{{- define "pravah.artifact.secretName" -}}
{{- if .Values.externalArtifact.existingSecret }}
{{- .Values.externalArtifact.existingSecret }}
{{- else }}
{{- include "pravah.secretName" . }}
{{- end }}
{{- end }}

{{/*
Vault — bundled dev server or external cluster
*/}}
{{- define "pravah.vault.hostname" -}}
{{- if .Values.vault.enabled }}
{{- printf "%s-vault" (include "pravah.fullname" .) }}
{{- end }}
{{- end }}

{{- define "pravah.vault.address" -}}
{{- if .Values.vault.enabled }}
{{- printf "http://%s:8200" (include "pravah.vault.hostname" .) }}
{{- else }}
{{- .Values.externalVault.address }}
{{- end }}
{{- end }}

{{/*
SMTP hostname — bundled Mailhog or external
*/}}
{{- define "pravah.smtp.host" -}}
{{- if .Values.mailhog.enabled }}
{{- printf "%s-mailhog" (include "pravah.fullname" .) }}
{{- else if .Values.smtp.host }}
{{- .Values.smtp.host }}
{{- else }}
{{- "localhost" }}
{{- end }}
{{- end }}

{{/*
Public hooks base URL for scheduler webhooks
*/}}
{{- define "pravah.hooksBaseUrl" -}}
{{- if .Values.pravah.hooksBaseUrl }}
{{- .Values.pravah.hooksBaseUrl }}
{{- else }}
{{- printf "http://%s-gateway:%v/api/v1/hooks" (include "pravah.fullname" .) (index .Values.services "gateway").port }}
{{- end }}
{{- end }}

{{/*
Secret name for credentials
*/}}
{{- define "pravah.secretName" -}}
{{- if .Values.externalSecrets.enabled }}
{{- required "externalSecrets.secretName is required when externalSecrets.enabled is true" .Values.externalSecrets.secretName }}
{{- else }}
{{- printf "%s-credentials" (include "pravah.fullname" .) }}
{{- end }}
{{- end }}

{{- define "pravah.internalService.secretName" -}}
{{- if .Values.pravah.internalServiceExistingSecret }}
{{- .Values.pravah.internalServiceExistingSecret }}
{{- else }}
{{- include "pravah.secretName" . }}
{{- end }}
{{- end }}

{{- define "pravah.internalService.secretKey" -}}
{{- .Values.pravah.internalServiceExistingSecretKey | default "internal-service-secret" }}
{{- end }}

{{- define "pravah.runnerBootstrap.secretName" -}}
{{- if .Values.pravah.runnerBootstrapExistingSecret }}
{{- .Values.pravah.runnerBootstrapExistingSecret }}
{{- else if .Values.pravah.runnerBootstrapSecret }}
{{- include "pravah.secretName" . }}
{{- else }}
{{- include "pravah.internalService.secretName" . }}
{{- end }}
{{- end }}

{{- define "pravah.runnerBootstrap.secretKey" -}}
{{- if .Values.pravah.runnerBootstrapExistingSecret }}
{{- .Values.pravah.runnerBootstrapExistingSecretKey | default "runner-bootstrap-secret" }}
{{- else if .Values.pravah.runnerBootstrapSecret }}
{{- "runner-bootstrap-secret" }}
{{- else }}
{{- include "pravah.internalService.secretKey" . }}
{{- end }}
{{- end }}

{{- define "pravah.jvmOptions" -}}
{{- .Values.jvmOptions | default "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError" }}
{{- end }}

{{/*
Postgres secret reference
*/}}
{{- define "pravah.postgres.secretName" -}}
{{- if and (not .Values.postgres.enabled) .Values.externalPostgres.existingSecret }}
{{- .Values.externalPostgres.existingSecret }}
{{- else }}
{{- include "pravah.secretName" . }}
{{- end }}
{{- end }}

{{/*
Service hostname helper
*/}}
{{- define "pravah.service.hostname" -}}
{{- $root := index . 0 }}
{{- $svcName := index . 1 }}
{{- printf "%s-%s" (include "pravah.fullname" $root) $svcName }}
{{- end }}

{{/*
Image reference
*/}}
{{- define "pravah.image" -}}
{{- $root := index . 0 }}
{{- $svcKey := index . 1 }}
{{- $registry := $root.Values.global.imageRegistry | default $root.Values.image.registry }}
{{- $prefix := $root.Values.image.repositoryPrefix }}
{{- $tag := $root.Values.image.tag }}
{{- if $registry }}
{{- printf "%s/%s-%s:%s" $registry $prefix $svcKey $tag }}
{{- else }}
{{- printf "%s-%s:%s" $prefix $svcKey $tag }}
{{- end }}
{{- end }}

{{/*
Common environment variables for all services
*/}}
{{- define "pravah.commonEnv" -}}
{{- $root := .root -}}
{{- $svc := .svc -}}
{{- $svcKey := .svcKey }}
- name: SPRING_PROFILES_ACTIVE
  value: k8s
- name: JAVA_TOOL_OPTIONS
  value: {{ include "pravah.jvmOptions" $root | quote }}
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.postgres.secretName" $root }}
      key: {{ if and (not $root.Values.postgres.enabled) $root.Values.externalPostgres.existingSecret }}{{ $root.Values.externalPostgres.existingSecretUsernameKey }}{{ else }}postgres-username{{ end }}
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.postgres.secretName" $root }}
      key: {{ if and (not $root.Values.postgres.enabled) $root.Values.externalPostgres.existingSecret }}{{ $root.Values.externalPostgres.existingSecretPasswordKey }}{{ else }}postgres-password{{ end }}
- name: PRAVAH_INTERNAL_SERVICE_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.internalService.secretName" $root }}
      key: {{ include "pravah.internalService.secretKey" $root }}
- name: PRAVAH_JWKS_URL
  value: {{ printf "http://%s-tenant-service:%v/.well-known/jwks.json" (include "pravah.fullname" $root) (index $root.Values.services "tenant-service").port | quote }}
- name: PRAVAH_JWT_ISSUER
  value: {{ $root.Values.pravah.jwtIssuer | quote }}
{{- if $svc.needsPostgres }}
{{- $dbName := $svc.database -}}
{{- if not $root.Values.postgres.enabled -}}
{{- $dbName = index $root.Values.externalPostgres.databases $svcKey | default $svc.database -}}
{{- end }}
- name: SPRING_DATASOURCE_URL
  value: {{ include "pravah.pgbouncer.datasourceUrl" (list $root $dbName) | quote }}
{{- end }}
{{- if $svc.needsKafka }}
- name: KAFKA_BOOTSTRAP_SERVERS
  value: {{ include "pravah.kafka.bootstrapServers" $root | quote }}
- name: SPRING_KAFKA_BOOTSTRAP_SERVERS
  value: {{ include "pravah.kafka.bootstrapServers" $root | quote }}
{{- if and (not $root.Values.kafka.enabled) $root.Values.externalKafka.sasl.enabled }}
- name: SPRING_KAFKA_PROPERTIES_SASL_MECHANISM
  value: {{ $root.Values.externalKafka.sasl.mechanism | quote }}
- name: SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL
  value: SASL_SSL
- name: SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG
  valueFrom:
    secretKeyRef:
      name: {{ $root.Values.externalKafka.sasl.existingSecret | default (include "pravah.secretName" $root) }}
      key: {{ $root.Values.externalKafka.sasl.existingSecretJaasKey | default "kafka-jaas-config" }}
{{- end }}
{{- end }}
{{- if $svc.needsRedis }}
- name: REDIS_HOST
  value: {{ include "pravah.redis.hostname" $root | quote }}
- name: REDIS_PORT
  value: {{ include "pravah.redis.port" $root | quote }}
- name: SPRING_DATA_REDIS_HOST
  value: {{ include "pravah.redis.hostname" $root | quote }}
- name: SPRING_DATA_REDIS_PORT
  value: {{ include "pravah.redis.port" $root | quote }}
- name: PRAVAH_REALTIME_REDIS_ENABLED
  value: {{ $root.Values.pravah.realtimeRedisEnabled | quote }}
- name: PRAVAH_WS_ALLOWED_ORIGINS
  value: {{ $root.Values.pravah.wsAllowedOrigins | quote }}
{{- if and (not $root.Values.redis.enabled) (or $root.Values.externalRedis.password $root.Values.externalRedis.existingSecret) }}
- name: SPRING_DATA_REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ $root.Values.externalRedis.existingSecret | default (include "pravah.secretName" $root) }}
      key: {{ $root.Values.externalRedis.existingSecretPasswordKey | default "redis-password" }}
{{- end }}
{{- if and (not $root.Values.redis.enabled) $root.Values.externalRedis.tls.enabled }}
- name: SPRING_DATA_REDIS_SSL_ENABLED
  value: "true"
{{- end }}
{{- end }}
{{- if and $svc.needsVault (or $root.Values.vault.enabled $root.Values.externalVault.address) }}
- name: PRAVAH_VAULT_ENABLED
  value: "true"
- name: VAULT_ADDR
  value: {{ include "pravah.vault.address" $root | quote }}
{{- if $root.Values.vault.enabled }}
- name: PRAVAH_VAULT_AUTH_METHOD
  value: token
- name: VAULT_TOKEN
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.secretName" $root }}
      key: vault-dev-root-token
{{- else }}
- name: PRAVAH_VAULT_AUTH_METHOD
  value: kubernetes
- name: PRAVAH_VAULT_K8S_MOUNT_PATH
  value: {{ $root.Values.externalVault.kubernetesMountPath | quote }}
- name: PRAVAH_VAULT_K8S_ROLE
  value: {{ $svc.vaultKubernetesRole | default (printf "pravah-%s" $svcKey) | quote }}
{{- end }}
{{- end }}
{{- if eq $svcKey "tenant-service" }}
- name: PRAVAH_AUTH_REGISTRATION_TENANT_ID
  value: {{ $root.Values.pravah.registrationTenantId | quote }}
- name: PRAVAH_FRONTEND_URL
  value: {{ $root.Values.pravah.frontendBaseUrl | quote }}
- name: PRAVAH_AUTH_REFRESH_COOKIE_SECURE
  value: {{ $root.Values.pravah.authRefreshCookieSecure | quote }}
- name: PRAVAH_MAIL_FROM
  value: {{ $root.Values.pravah.mailFrom | quote }}
- name: MAIL_HOST
  value: {{ include "pravah.smtp.host" $root | quote }}
- name: MAIL_PORT
  value: {{ $root.Values.smtp.port | default 1025 | quote }}
{{- if $root.Values.smtp.username }}
- name: MAIL_USERNAME
  value: {{ $root.Values.smtp.username | quote }}
{{- end }}
{{- if or $root.Values.smtp.password $root.Values.smtp.existingSecret }}
- name: MAIL_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ $root.Values.smtp.existingSecret | default (include "pravah.secretName" $root) }}
      key: {{ $root.Values.smtp.existingSecretPasswordKey | default "smtp-password" }}
{{- end }}
{{- end }}
{{- if eq $svcKey "execution-service" }}
- name: PIPELINE_SERVICE_BASE_URL
  value: {{ printf "http://%s-pipeline-service:%v" (include "pravah.fullname" $root) (index $root.Values.services "pipeline-service").port | quote }}
- name: RUNNER_SERVICE_BASE_URL
  value: {{ printf "http://%s-runner-service:%v" (include "pravah.fullname" $root) (index $root.Values.services "runner-service").port | quote }}
- name: PRAVAH_ARTIFACT_ENABLED
  value: "true"
- name: PRAVAH_ARTIFACT_ENDPOINT
  value: {{ include "pravah.artifact.endpoint" $root | quote }}
- name: PRAVAH_ARTIFACT_REGION
  value: {{ $root.Values.externalArtifact.region | default "us-east-1" | quote }}
- name: PRAVAH_ARTIFACT_BUCKET
  value: {{ if $root.Values.minio.enabled }}{{ $root.Values.minio.bucket | quote }}{{ else }}{{ $root.Values.externalArtifact.bucket | quote }}{{ end }}
{{- if $root.Values.externalArtifact.irsa.enabled }}
- name: PRAVAH_ARTIFACT_IRSA_ENABLED
  value: "true"
{{- else }}
- name: PRAVAH_ARTIFACT_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.artifact.secretName" $root }}
      key: {{ if $root.Values.externalArtifact.existingSecret }}{{ $root.Values.externalArtifact.existingSecretAccessKeyKey }}{{ else if $root.Values.minio.enabled }}minio-access-key{{ else }}artifact-access-key{{ end }}
- name: PRAVAH_ARTIFACT_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.artifact.secretName" $root }}
      key: {{ if $root.Values.externalArtifact.existingSecret }}{{ $root.Values.externalArtifact.existingSecretSecretKeyKey }}{{ else if $root.Values.minio.enabled }}minio-secret-key{{ else }}artifact-secret-key{{ end }}
{{- end }}
{{- end }}
{{- if eq $svcKey "scheduler-service" }}
- name: EXECUTION_SERVICE_BASE_URL
  value: {{ printf "http://%s-execution-service:%v" (include "pravah.fullname" $root) (index $root.Values.services "execution-service").port | quote }}
- name: PRAVAH_HOOKS_BASE_URL
  value: {{ include "pravah.hooksBaseUrl" $root | quote }}
- name: PRAVAH_RATE_LIMIT_FAIL_OPEN
  value: {{ $root.Values.pravah.rateLimitFailOpen | quote }}
{{- end }}
{{- if eq $svcKey "notification-service" }}
- name: PRAVAH_UI_BASE_URL
  value: {{ $root.Values.pravah.frontendBaseUrl | quote }}
- name: SMTP_HOST
  value: {{ include "pravah.smtp.host" $root | quote }}
- name: SMTP_PORT
  value: {{ $root.Values.smtp.port | default 1025 | quote }}
- name: SMTP_AUTH
  value: {{ $root.Values.smtp.auth | default "false" | quote }}
- name: SMTP_STARTTLS
  value: {{ $root.Values.smtp.starttls | default "false" | quote }}
{{- if $root.Values.smtp.username }}
- name: SMTP_USERNAME
  value: {{ $root.Values.smtp.username | quote }}
{{- end }}
{{- if or $root.Values.smtp.password $root.Values.smtp.existingSecret }}
- name: SMTP_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ $root.Values.smtp.existingSecret | default (include "pravah.secretName" $root) }}
      key: {{ $root.Values.smtp.existingSecretPasswordKey | default "smtp-password" }}
{{- end }}
{{- end }}
{{- if eq $svcKey "runner-service" }}
- name: EXECUTION_SERVICE_BASE_URL
  value: {{ printf "http://%s-execution-service:%v" (include "pravah.fullname" $root) (index $root.Values.services "execution-service").port | quote }}
- name: PRAVAH_RUNNER_BOOTSTRAP_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ include "pravah.runnerBootstrap.secretName" $root }}
      key: {{ include "pravah.runnerBootstrap.secretKey" $root }}
{{- if $root.Values.runnerGrpcTls.enabled }}
- name: PRAVAH_RUNNER_GRPC_TLS_ENABLED
  value: "true"
- name: PRAVAH_RUNNER_GRPC_TLS_CERT_CHAIN
  value: {{ printf "%s/%s" $root.Values.runnerGrpcTls.mountPath $root.Values.runnerGrpcTls.certChainKey | quote }}
- name: PRAVAH_RUNNER_GRPC_TLS_PRIVATE_KEY
  value: {{ printf "%s/%s" $root.Values.runnerGrpcTls.mountPath $root.Values.runnerGrpcTls.privateKeyKey | quote }}
{{- if $root.Values.runnerGrpcTls.requireClientAuth }}
- name: PRAVAH_RUNNER_GRPC_TLS_CLIENT_CA
  value: {{ printf "%s/%s" $root.Values.runnerGrpcTls.mountPath $root.Values.runnerGrpcTls.clientCaKey | quote }}
{{- end }}
{{- end }}
{{- if $root.Values.runnerPki.enabled }}
- name: PRAVAH_RUNNER_PKI_ENABLED
  value: "true"
- name: PRAVAH_RUNNER_PKI_ISSUE_PATH
  value: {{ $root.Values.runnerPki.issuePath | quote }}
- name: PRAVAH_RUNNER_PKI_TTL
  value: {{ $root.Values.runnerPki.ttl | quote }}
- name: PRAVAH_RUNNER_PKI_SPIFFE_TRUST_DOMAIN
  value: {{ $root.Values.runnerPki.spiffeTrustDomain | quote }}
{{- end }}
{{- end }}
{{- include "pravah.tracingEnv" (dict "root" $root "serviceName" $svcKey) }}
{{- range $k, $v := $svc.extraEnv }}
- name: {{ $k }}
  value: {{ $v | quote }}
{{- end }}
{{- end }}

{{/*
OpenTelemetry tracing environment variables (pathway #11).
Usage: include "pravah.tracingEnv" (dict "root" $ "serviceName" "gateway")
*/}}
{{- define "pravah.tracingEnv" -}}
{{- $root := .root -}}
{{- $serviceName := .serviceName -}}
{{- if and $root.Values.observability.tracing.enabled $root.Values.observability.tracing.endpoint }}
- name: MANAGEMENT_TRACING_SAMPLING_PROBABILITY
  value: {{ $root.Values.observability.tracing.samplingProbability | quote }}
- name: MANAGEMENT_OTLP_TRACING_ENDPOINT
  value: {{ $root.Values.observability.tracing.endpoint | quote }}
- name: OTEL_SERVICE_NAME
  value: {{ $serviceName | quote }}
{{- end }}
{{- end }}

{{/*
Init containers: wait for bundled dependencies before service start.
*/}}
{{- define "pravah.serviceInitContainers" -}}
{{- $root := .root -}}
{{- $svc := .svc -}}
{{- $svcKey := .svcKey -}}
{{- if $root.Values.dependencyWait.enabled }}
{{- if and $svc.needsPostgres $root.Values.postgres.enabled }}
- name: wait-postgres
  image: {{ $root.Values.postgres.image }}
  securityContext:
    {{- toYaml $root.Values.containerSecurityContext | nindent 4 }}
  command:
    - sh
    - -c
    - |
      until pg_isready -h {{ include "pravah.postgres.hostname" $root }} -p {{ include "pravah.postgres.port" $root }} -U {{ $root.Values.postgres.auth.username }}; do
        echo "waiting for postgres..."
        sleep 2
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 50m
      memory: 32Mi
{{- end }}
{{- if and $svc.needsPostgres $root.Values.pgbouncer.enabled }}
- name: wait-pgbouncer
  image: {{ $root.Values.postgres.image }}
  securityContext:
    {{- toYaml $root.Values.containerSecurityContext | nindent 4 }}
  env:
    - name: PGUSER
      valueFrom:
        secretKeyRef:
          name: {{ include "pravah.postgres.secretName" $root }}
          key: {{ if and (not $root.Values.postgres.enabled) $root.Values.externalPostgres.existingSecret }}{{ $root.Values.externalPostgres.existingSecretUsernameKey }}{{ else }}postgres-username{{ end }}
  command:
    - sh
    - -c
    - |
      until pg_isready -h {{ include "pravah.db.hostname" $root }} -p {{ include "pravah.db.port" $root }} -U "$PGUSER"; do
        echo "waiting for pgbouncer..."
        sleep 2
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 50m
      memory: 32Mi
{{- end }}
{{- if and $svc.needsKafka $root.Values.kafka.enabled }}
- name: wait-kafka
  image: {{ $root.Values.dependencyWait.image }}
  command:
    - sh
    - -c
    - |
      until nc -z {{ include "pravah.kafka.hostname" $root }} 9092; do
        echo "waiting for kafka..."
        sleep 2
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 50m
      memory: 32Mi
{{- end }}
{{- if and $svc.needsRedis $root.Values.redis.enabled }}
- name: wait-redis
  image: {{ $root.Values.dependencyWait.image }}
  command:
    - sh
    - -c
    - |
      until nc -z {{ include "pravah.redis.hostname" $root }} {{ include "pravah.redis.port" $root }}; do
        echo "waiting for redis..."
        sleep 2
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 50m
      memory: 32Mi
{{- end }}
{{- if and (eq $svcKey "execution-service") $root.Values.minio.enabled }}
- name: wait-minio
  image: {{ $root.Values.dependencyWait.image }}
  command:
    - sh
    - -c
    - |
      until nc -z {{ include "pravah.minio.hostname" $root }} 9000; do
        echo "waiting for minio..."
        sleep 2
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 50m
      memory: 32Mi
{{- end }}
{{- if and $svc.needsVault $root.Values.vault.enabled }}
- name: wait-vault
  image: {{ $root.Values.dependencyWait.image }}
  command:
    - sh
    - -c
    - |
      until nc -z {{ include "pravah.vault.hostname" $root }} 8200; do
        echo "waiting for vault..."
        sleep 2
      done
  resources:
    requests:
      cpu: 10m
      memory: 16Mi
    limits:
      cpu: 50m
      memory: 32Mi
{{- end }}
{{- end }}
{{- end }}

{{/*
Pod affinity for HA spread
*/}}
{{- define "pravah.podAntiAffinity" -}}
{{- $root := .root }}
{{- $svcKey := .svcKey }}
{{- $svc := .svc }}
{{- if $svc.affinity }}
{{ toYaml $svc.affinity }}
{{- else if gt (int $svc.replicaCount) 1 }}
podAntiAffinity:
  preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchLabels:
            app.kubernetes.io/component: {{ $svcKey }}
            {{- include "pravah.selectorLabels" $root | nindent 12 }}
        topologyKey: kubernetes.io/hostname
{{- end }}
{{- end }}

{{/*
Topology spread constraints
*/}}
{{- define "pravah.topologySpreadConstraints" -}}
{{- $root := .root }}
{{- $svcKey := .svcKey }}
{{- if and $root.Values.topologySpreadConstraints.enabled (gt (int .svc.replicaCount) 1) }}
- maxSkew: {{ $root.Values.topologySpreadConstraints.maxSkew }}
  topologyKey: {{ $root.Values.topologySpreadConstraints.topologyKey }}
  whenUnsatisfiable: {{ $root.Values.topologySpreadConstraints.whenUnsatisfiable }}
  labelSelector:
    matchLabels:
      app.kubernetes.io/component: {{ $svcKey }}
      {{- include "pravah.selectorLabels" $root | nindent 6 }}
{{- end }}
{{- end }}
