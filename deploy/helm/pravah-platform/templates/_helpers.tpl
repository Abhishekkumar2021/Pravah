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
Postgres hostname - bundled or external
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
  value: {{ printf "jdbc:postgresql://%s:%v/%s" (include "pravah.postgres.hostname" $root) (include "pravah.postgres.port" $root) $dbName | quote }}
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
{{- if and (not $root.Values.redis.enabled) $root.Values.externalRedis.password }}
- name: SPRING_DATA_REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ $root.Values.externalRedis.existingSecret | default (include "pravah.secretName" $root) }}
      key: {{ $root.Values.externalRedis.existingSecretPasswordKey | default "redis-password" }}
{{- end }}
{{- end }}
{{- if eq $svcKey "tenant-service" }}
- name: PRAVAH_AUTH_REGISTRATION_TENANT_ID
  value: {{ $root.Values.pravah.registrationTenantId | quote }}
- name: PRAVAH_FRONTEND_URL
  value: {{ $root.Values.pravah.frontendBaseUrl | quote }}
{{- end }}
{{- if eq $svcKey "execution-service" }}
- name: PIPELINE_SERVICE_BASE_URL
  value: {{ printf "http://%s-pipeline-service:%v" (include "pravah.fullname" $root) (index $root.Values.services "pipeline-service").port | quote }}
{{- end }}
{{- if eq $svcKey "scheduler-service" }}
- name: EXECUTION_SERVICE_BASE_URL
  value: {{ printf "http://%s-execution-service:%v" (include "pravah.fullname" $root) (index $root.Values.services "execution-service").port | quote }}
{{- end }}
{{- range $k, $v := $svc.extraEnv }}
- name: {{ $k }}
  value: {{ $v | quote }}
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
