package io.pravah.runnerservice.grpc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.pravah.common.grpc.RunnerCertificateIdentity;
import io.pravah.proto.common.Label;
import io.pravah.proto.runner.*;
import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.infrastructure.grpc.RunnerGrpcIdentityContext;
import io.pravah.runnerservice.service.JobAssignmentService;
import io.pravah.runnerservice.service.RunnerConnectionManager;
import io.pravah.runnerservice.service.RunnerService;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * gRPC service implementation for runner management. Note: This service needs to be registered with
 * a gRPC server manually or via grpc-spring-boot-starter if added to dependencies.
 */
@Service
public class RunnerServiceGrpcImpl extends RunnerServiceGrpc.RunnerServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(RunnerServiceGrpcImpl.class);

  private final RunnerService runnerService;
  private final RunnerConnectionManager connectionManager;
  private final JobAssignmentService jobAssignmentService;
  private final ObjectMapper objectMapper;

  public RunnerServiceGrpcImpl(
      RunnerService runnerService,
      RunnerConnectionManager connectionManager,
      JobAssignmentService jobAssignmentService,
      ObjectMapper objectMapper) {
    this.runnerService = runnerService;
    this.connectionManager = connectionManager;
    this.jobAssignmentService = jobAssignmentService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void registerRunner(
      RegisterRunnerRequest request, StreamObserver<RegisterRunnerResponse> responseObserver) {
    try {
      UUID tenantId = requireTenantId();

      var registerRequest =
          new RunnerService.RegisterRequest(
              request.getName(),
              request.getVersion(),
              labelsToMap(request.getLabelsList()),
              request.getCapabilities().getMaxConcurrentJobs(),
              request.getCapabilities().getSupportedExecutorsList(),
              request.getCapabilities().getAvailableMemoryBytes(),
              request.getCapabilities().getAvailableCpus(),
              request.getRegistrationToken());

      var result = runnerService.registerRunner(tenantId, registerRequest);

      RegisterRunnerResponse.Builder responseBuilder =
          RegisterRunnerResponse.newBuilder()
              .setRunnerId(result.runnerId().toString())
              .setToken(result.token())
              .setHeartbeatIntervalSeconds(result.heartbeatIntervalSeconds());
      result
          .mtlsCertificate()
          .ifPresent(
              cert ->
                  responseBuilder.setMtls(
                      RunnerMtlsCertificate.newBuilder()
                          .setCertificatePem(cert.certificatePem())
                          .setPrivateKeyPem(cert.privateKeyPem())
                          .setCaChainPem(cert.issuingCaPem())
                          .build()));
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();

    } catch (IllegalArgumentException e) {
      Status status =
          e.getMessage() != null && e.getMessage().contains("Invalid registration token")
              ? Status.PERMISSION_DENIED
              : Status.ALREADY_EXISTS;
      responseObserver.onError(status.withDescription(e.getMessage()).asRuntimeException());
    } catch (Exception e) {
      log.error("Failed to register runner", e);
      responseObserver.onError(
          Status.INTERNAL.withDescription("Registration failed").asRuntimeException());
    }
  }

  @Override
  public StreamObserver<RunnerMessage> connect(StreamObserver<ServerMessage> responseObserver) {
    return new StreamObserver<>() {
      private UUID runnerId;
      private boolean authenticated = false;

      @Override
      public void onNext(RunnerMessage message) {
        if (!authenticated && message.getMessageCase() != RunnerMessage.MessageCase.HEARTBEAT) {
          log.warn("Ignoring message before stream authentication: {}", message.getMessageCase());
          return;
        }
        switch (message.getMessageCase()) {
          case HEARTBEAT -> handleHeartbeat(message.getHeartbeat(), responseObserver);
          case JOB_STATUS -> handleJobStatus(message.getJobStatus());
          case LOG_CHUNK -> handleLogChunk(message.getLogChunk());
          default -> log.warn("Unknown message type from runner");
        }
      }

      private void handleHeartbeat(Heartbeat heartbeat, StreamObserver<ServerMessage> observer) {
        try {
          UUID heartbeatRunnerId = UUID.fromString(heartbeat.getRunnerId());

          if (!authenticated) {
            Optional<RunnerCertificateIdentity> certIdentity = RunnerGrpcIdentityContext.current();
            UUID metadataTenantId = requireTenantIdOrNull();

            if (certIdentity.isPresent()) {
              UUID certRunnerId = certIdentity.get().runnerId();
              if (!heartbeatRunnerId.equals(certRunnerId)) {
                rejectStream(
                    observer,
                    Status.UNAUTHENTICATED.withDescription(
                        "runner_id does not match client certificate identity"));
                return;
              }
              var resolved =
                  runnerService.resolveCertificateIdentity(
                      certRunnerId, certIdentity.get().tenantId(), metadataTenantId);
              if (resolved.isEmpty()) {
                rejectStream(
                    observer,
                    Status.UNAUTHENTICATED.withDescription(
                        "Unknown runner or tenant mismatch for certificate identity"));
                return;
              }
              this.runnerId = certRunnerId;
            } else {
              String token = heartbeat.getToken();
              if (token == null || token.isBlank()) {
                rejectStream(
                    observer, Status.UNAUTHENTICATED.withDescription("Missing runner token"));
                return;
              }
              var validated = runnerService.validateToken(heartbeatRunnerId, token);
              if (validated.isEmpty()) {
                rejectStream(
                    observer, Status.UNAUTHENTICATED.withDescription("Invalid runner token"));
                return;
              }
              if (metadataTenantId != null
                  && !validated.get().getTenantId().equals(metadataTenantId)) {
                rejectStream(
                    observer, Status.PERMISSION_DENIED.withDescription("Runner tenant mismatch"));
                return;
              }
              this.runnerId = heartbeatRunnerId;
            }

            connectionManager.register(runnerId, responseObserver);
            runnerService.markOnline(runnerId);
            authenticated = true;
            log.info(
                "Runner stream authenticated: runnerId={}, mtls={}",
                runnerId,
                certIdentity.isPresent());
          } else if (!heartbeatRunnerId.equals(runnerId)) {
            log.warn(
                "Heartbeat runner_id mismatch: expected={}, got={}", runnerId, heartbeatRunnerId);
            return;
          }

          runnerService.processHeartbeat(
              heartbeatRunnerId,
              new RunnerService.HeartbeatData(
                  heartbeat.getMetrics().getCpuUsagePercent(),
                  heartbeat.getMetrics().getMemoryUsedBytes(),
                  heartbeat.getMetrics().getMemoryTotalBytes(),
                  heartbeat.getMetrics().getActiveJobs(),
                  heartbeat.getMetrics().getDiskAvailableBytes()));

          // Send ack
          observer.onNext(
              ServerMessage.newBuilder()
                  .setHeartbeatAck(
                      HeartbeatAck.newBuilder()
                          .setServerTimestamp(System.currentTimeMillis())
                          .build())
                  .build());

        } catch (Exception e) {
          log.error("Error processing heartbeat", e);
        }
      }

      private void handleJobStatus(JobStatusUpdate status) {
        if (!authenticated || runnerId == null) {
          log.warn("Ignoring job status before stream authentication");
          return;
        }
        log.info(
            "Job status update: jobId={}, status={}, runnerId={}",
            status.getJobId(),
            status.getStatus(),
            runnerId);
        UUID jobId = UUID.fromString(status.getJobId());
        int exitCode = status.getExitCode();
        java.util.Map<String, Object> output = parseOutputJson(status.getOutputJson());
        switch (status.getStatus()) {
          case JOB_STATUS_RUNNING -> jobAssignmentService.markStarted(jobId, runnerId);
          case JOB_STATUS_SUCCEEDED ->
              jobAssignmentService.markCompleted(jobId, runnerId, true, exitCode, output);
          case JOB_STATUS_FAILED ->
              jobAssignmentService.markCompleted(jobId, runnerId, false, exitCode, output);
          case JOB_STATUS_CANCELLED, JOB_STATUS_TIMED_OUT ->
              jobAssignmentService.markCompleted(jobId, runnerId, false, exitCode, output);
          default -> {}
        }
      }

      private void rejectStream(StreamObserver<ServerMessage> observer, Status status) {
        observer.onError(status.asRuntimeException());
        cleanup();
      }

      private void handleLogChunk(JobLogChunk chunk) {
        log.debug(
            "Log chunk received: jobId={}, sequence={}, size={}",
            chunk.getJobId(),
            chunk.getSequence(),
            chunk.getData().size());
        // TODO: Forward to log aggregation
      }

      @Override
      public void onError(Throwable t) {
        log.warn("Runner stream error: runnerId={}", runnerId, t);
        cleanup();
      }

      @Override
      public void onCompleted() {
        log.info("Runner stream completed: runnerId={}", runnerId);
        cleanup();
        responseObserver.onCompleted();
      }

      private void cleanup() {
        if (runnerId != null) {
          connectionManager.unregister(runnerId);
          runnerService.markOffline(runnerId);
        }
      }
    };
  }

  @Override
  public void getRunner(GetRunnerRequest request, StreamObserver<RunnerInfo> responseObserver) {
    try {
      UUID tenantId = requireTenantId();
      UUID runnerId = UUID.fromString(request.getRunnerId());

      runnerService
          .getRunner(tenantId, runnerId)
          .map(this::toRunnerInfo)
          .ifPresentOrElse(
              info -> {
                responseObserver.onNext(info);
                responseObserver.onCompleted();
              },
              () ->
                  responseObserver.onError(
                      Status.NOT_FOUND.withDescription("Runner not found").asRuntimeException()));

    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("Invalid runner ID").asRuntimeException());
    } catch (Exception e) {
      log.error("Failed to get runner", e);
      responseObserver.onError(
          Status.INTERNAL.withDescription("Failed to get runner").asRuntimeException());
    }
  }

  @Override
  public void listRunners(
      ListRunnersRequest request, StreamObserver<ListRunnersResponse> responseObserver) {
    try {
      UUID tenantId = requireTenantId();
      List<Runner> runners = runnerService.listRunners(tenantId);

      var response =
          ListRunnersResponse.newBuilder()
              .addAllRunners(runners.stream().map(this::toRunnerInfo).toList())
              .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("Failed to list runners", e);
      responseObserver.onError(
          Status.INTERNAL.withDescription("Failed to list runners").asRuntimeException());
    }
  }

  private java.util.Map<String, Object> parseOutputJson(String outputJson) {
    if (outputJson == null || outputJson.isBlank()) {
      return java.util.Map.of();
    }
    try {
      return objectMapper.readValue(outputJson, new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("Failed to parse runner output JSON", e);
      return java.util.Map.of("raw_output", outputJson);
    }
  }

  private UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context not set");
    }
    return tenantId;
  }

  private UUID requireTenantIdOrNull() {
    return TenantContext.getCurrentTenantId();
  }

  private java.util.Map<String, String> labelsToMap(List<Label> labels) {
    var map = new java.util.HashMap<String, String>();
    for (Label label : labels) {
      map.put(label.getKey(), label.getValue());
    }
    return map;
  }

  private RunnerInfo toRunnerInfo(Runner runner) {
    var builder =
        RunnerInfo.newBuilder()
            .setRunnerId(runner.getId().toString())
            .setName(runner.getName())
            .setVersion(runner.getVersion())
            .setStatus(toProtoStatus(runner.getStatus()))
            .setCapabilities(
                RunnerCapabilities.newBuilder()
                    .setMaxConcurrentJobs(runner.getMaxConcurrentJobs())
                    .setAvailableMemoryBytes(runner.getAvailableMemoryBytes())
                    .setAvailableCpus(runner.getAvailableCpus())
                    .build())
            .setActiveJobs(runner.getActiveJobs())
            .setRegisteredAt(runner.getRegisteredAt().toEpochMilli());

    if (runner.getLastHeartbeatAt() != null) {
      builder.setLastHeartbeatAt(runner.getLastHeartbeatAt().toEpochMilli());
    }

    for (var entry : runner.getLabels().entrySet()) {
      builder.addLabels(
          Label.newBuilder().setKey(entry.getKey()).setValue(entry.getValue()).build());
    }

    if (runner.getSupportedExecutors() != null) {
      for (String executor : runner.getSupportedExecutors().split(",")) {
        builder.getCapabilitiesBuilder().addSupportedExecutors(executor.trim());
      }
    }

    return builder.build();
  }

  private RunnerStatus toProtoStatus(io.pravah.runnerservice.domain.RunnerStatus status) {
    return switch (status) {
      case OFFLINE -> RunnerStatus.RUNNER_STATUS_OFFLINE;
      case ONLINE -> RunnerStatus.RUNNER_STATUS_ONLINE;
      case BUSY -> RunnerStatus.RUNNER_STATUS_BUSY;
      case DRAINING -> RunnerStatus.RUNNER_STATUS_DRAINING;
    };
  }
}
