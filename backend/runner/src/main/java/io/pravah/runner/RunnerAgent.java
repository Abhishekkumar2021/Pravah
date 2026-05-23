package io.pravah.runner;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.pravah.common.grpc.GrpcTlsConfig;
import io.pravah.proto.runner.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runner agent: registers with the control plane and maintains a heartbeat stream.
 *
 * <p>Job execution uses local Docker/shell executors; DuckDB transforms run via embedded Python
 * when configured in job spec.
 */
public class RunnerAgent implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RunnerAgent.class);

  private final String serverHost;
  private final int serverPort;
  private final UUID tenantId;
  private final String bootstrapSecret;
  private final String registrationToken;
  private final String existingRunnerId;
  private final String name;
  private final Map<String, String> labels;
  private final int maxJobs;
  private final String workDir;
  private final GrpcTlsConfig tlsConfig;

  private volatile String streamToken;

  private ManagedChannel channel;
  private RunnerServiceGrpc.RunnerServiceStub asyncStub;
  private RunnerServiceGrpc.RunnerServiceBlockingStub blockingStub;
  private ScheduledExecutorService scheduler;
  private java.util.concurrent.ExecutorService jobExecutor;
  private StreamObserver<RunnerMessage> requestObserver;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
  private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
  private final AtomicInteger activeJobs = new AtomicInteger(0);
  private String runnerId;

  public RunnerAgent(
      String serverHost,
      int serverPort,
      UUID tenantId,
      String bootstrapSecret,
      String registrationToken,
      String existingRunnerId,
      String name,
      Map<String, String> labels,
      int maxJobs,
      String workDir) {
    this(
        serverHost,
        serverPort,
        tenantId,
        bootstrapSecret,
        registrationToken,
        existingRunnerId,
        name,
        labels,
        maxJobs,
        workDir,
        GrpcTlsConfig.disabled());
  }

  public RunnerAgent(
      String serverHost,
      int serverPort,
      UUID tenantId,
      String bootstrapSecret,
      String registrationToken,
      String existingRunnerId,
      String name,
      Map<String, String> labels,
      int maxJobs,
      String workDir,
      GrpcTlsConfig tlsConfig) {
    this.serverHost = serverHost;
    this.serverPort = serverPort;
    this.tenantId = tenantId;
    this.bootstrapSecret = bootstrapSecret;
    this.registrationToken = registrationToken;
    this.existingRunnerId = existingRunnerId;
    this.name = name;
    this.labels = labels;
    this.maxJobs = maxJobs;
    this.workDir = workDir;
    this.tlsConfig = tlsConfig != null ? tlsConfig : GrpcTlsConfig.disabled();
    this.streamToken = registrationToken;
  }

  public void start() {
    try {
      channel = RunnerGrpcClientTls.channelBuilder(serverHost, serverPort, tlsConfig).build();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to configure gRPC TLS channel", e);
    }
    ClientInterceptor metadataInterceptor =
        new RunnerGrpcClientInterceptor(
            RunnerGrpcMetadata.registrationHeaders(tenantId, bootstrapSecret));
    asyncStub = RunnerServiceGrpc.newStub(channel).withInterceptors(metadataInterceptor);
    blockingStub = RunnerServiceGrpc.newBlockingStub(channel).withInterceptors(metadataInterceptor);

    if (existingRunnerId != null && !existingRunnerId.isBlank()) {
      runnerId = existingRunnerId;
      if (streamToken == null || streamToken.isBlank()) {
        throw new IllegalStateException("--token is required when --runner-id is set");
      }
    } else {
      register();
    }
    connectStream();

    running.set(true);
    scheduler =
        Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("runner-heartbeat"));
    jobExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory("runner-job"));
    scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 30, TimeUnit.SECONDS);

    log.info(
        "Runner agent started",
        kv("runnerId", runnerId),
        kv("workDir", workDir),
        kv("grpcTls", tlsConfig.enabled()));
  }

  private void register() {
    RegisterRunnerRequest.Builder request =
        RegisterRunnerRequest.newBuilder()
            .setName(name)
            .setVersion("0.1.0")
            .setCapabilities(
                RunnerCapabilities.newBuilder()
                    .setMaxConcurrentJobs(maxJobs)
                    .addSupportedExecutors("container")
                    .addSupportedExecutors("python")
                    .addSupportedExecutors("sql")
                    .addSupportedExecutors("shell")
                    .setAvailableMemoryBytes(Runtime.getRuntime().maxMemory())
                    .setAvailableCpus(Runtime.getRuntime().availableProcessors())
                    .build());

    labels.forEach(
        (k, v) ->
            request.addLabels(
                io.pravah.proto.common.Label.newBuilder().setKey(k).setValue(v).build()));

    if (registrationToken != null && !registrationToken.isBlank()) {
      request.setRegistrationToken(registrationToken);
    }

    RegisterRunnerResponse response = blockingStub.registerRunner(request.build());
    runnerId = response.getRunnerId();
    streamToken = response.getToken();
    log.info(
        "Registered with runner service",
        kv("runnerId", runnerId),
        kv("heartbeatInterval", response.getHeartbeatIntervalSeconds()),
        kv("tokenReceived", streamToken != null && !streamToken.isBlank()));
  }

  private void connectStream() {
    StreamObserver<ServerMessage> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(ServerMessage message) {
            if (message.hasHeartbeatAck()) {
              log.debug("Heartbeat acknowledged");
            } else if (message.hasJobAssignment()) {
              handleJobAssignment(message.getJobAssignment());
            } else if (message.hasJobCancellation()) {
              log.info(
                  "Job cancellation received",
                  kv("jobId", message.getJobCancellation().getJobId()));
            } else if (message.hasShutdown()) {
              log.warn("Shutdown requested", kv("reason", message.getShutdown().getReason()));
              shutdownRequested.set(true);
              running.set(false);
            }
          }

          @Override
          public void onError(Throwable t) {
            log.error("Runner stream error", t);
            requestObserver = null;
            if (!shutdownRequested.get()) {
              scheduleReconnect();
            } else {
              running.set(false);
            }
          }

          @Override
          public void onCompleted() {
            log.info("Runner stream completed");
            requestObserver = null;
            if (!shutdownRequested.get()) {
              scheduleReconnect();
            } else {
              running.set(false);
            }
          }
        };

    requestObserver = asyncStub.connect(responseObserver);
    reconnectAttempts.set(0);
    log.debug("gRPC stream connection initiated");
  }

  private void scheduleReconnect() {
    if (shutdownRequested.get() || scheduler == null) {
      return;
    }
    int attempt = reconnectAttempts.incrementAndGet();
    long delaySeconds = Math.min(60L, 1L << Math.min(attempt - 1, 6));
    log.warn(
        "Scheduling runner stream reconnect",
        kv("attempt", attempt),
        kv("delaySeconds", delaySeconds));
    scheduler.schedule(this::reconnectStream, delaySeconds, TimeUnit.SECONDS);
  }

  private void reconnectStream() {
    if (shutdownRequested.get() || !running.get()) {
      return;
    }
    try {
      connectStream();
      log.info("Runner stream reconnected", kv("runnerId", runnerId));
    } catch (Exception e) {
      log.warn("Runner stream reconnect failed", kv("error", e.getMessage()));
      scheduleReconnect();
    }
  }

  private void sendHeartbeat() {
    if (!running.get() || requestObserver == null || runnerId == null) {
      return;
    }
    try {
      RunnerMessage heartbeat =
          RunnerMessage.newBuilder()
              .setHeartbeat(
                  Heartbeat.newBuilder()
                      .setRunnerId(runnerId)
                      .setToken(streamToken != null ? streamToken : "")
                      .setTimestamp(System.currentTimeMillis())
                      .setMetrics(
                          RunnerMetrics.newBuilder()
                              .setCpuUsagePercent(0)
                              .setMemoryUsedBytes(
                                  Runtime.getRuntime().totalMemory()
                                      - Runtime.getRuntime().freeMemory())
                              .setMemoryTotalBytes(Runtime.getRuntime().maxMemory())
                              .setActiveJobs(activeJobs.get())
                              .setDiskAvailableBytes(workDir != null ? 0 : 0)
                              .build())
                      .build())
              .build();
      requestObserver.onNext(heartbeat);
    } catch (Exception e) {
      log.warn("Failed to send heartbeat", kv("error", e.getMessage()));
    }
  }

  private void handleJobAssignment(io.pravah.proto.runner.JobAssignment assignment) {
    String jobId = assignment.getJobId();
    log.info(
        "Received job assignment",
        kv("jobId", jobId),
        kv("executor", assignment.getSpec().getExecutor()));

    activeJobs.incrementAndGet();
    jobExecutor.execute(
        () -> {
          long startedAt = System.currentTimeMillis();
          try {
            reportJobStatus(jobId, JobStatus.JOB_STATUS_RUNNING, 0, startedAt, 0, null, Map.of());
            JobExecutor executor = new JobExecutor(workDir);
            JobExecutionResult result = executor.execute(assignment);
            reportJobStatus(
                jobId,
                result.exitCode() == 0
                    ? JobStatus.JOB_STATUS_SUCCEEDED
                    : JobStatus.JOB_STATUS_FAILED,
                result.exitCode(),
                startedAt,
                System.currentTimeMillis(),
                null,
                result.output());
          } catch (Exception e) {
            log.error("Job execution failed", kv("jobId", jobId), e);
            reportJobStatus(
                jobId,
                JobStatus.JOB_STATUS_FAILED,
                1,
                startedAt,
                System.currentTimeMillis(),
                e.getMessage(),
                Map.of());
          } finally {
            activeJobs.decrementAndGet();
          }
        });
  }

  private void reportJobStatus(
      String jobId,
      JobStatus status,
      int exitCode,
      long startedAt,
      long completedAt,
      String error,
      Map<String, Object> output) {
    if (requestObserver == null) {
      return;
    }
    JobStatusUpdate.Builder update =
        JobStatusUpdate.newBuilder()
            .setJobId(jobId)
            .setStatus(status)
            .setExitCode(exitCode)
            .setStartedAt(startedAt)
            .setCompletedAt(completedAt);
    if (error != null) {
      update.setErrorMessage(error);
    }
    String outputJson = RunnerOutputJson.toJson(output);
    if (!outputJson.isBlank()) {
      update.setOutputJson(outputJson);
    }
    requestObserver.onNext(RunnerMessage.newBuilder().setJobStatus(update.build()).build());
  }

  public void awaitTermination() throws InterruptedException {
    while (running.get()) {
      Thread.sleep(1000);
    }
  }

  @Override
  public void close() {
    shutdownRequested.set(true);
    running.set(false);
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    if (jobExecutor != null) {
      jobExecutor.shutdownNow();
    }
    if (requestObserver != null) {
      requestObserver.onCompleted();
    }
    if (channel != null) {
      channel.shutdown();
      try {
        channel.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class DaemonThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final java.util.concurrent.atomic.AtomicInteger threadNumber =
        new java.util.concurrent.atomic.AtomicInteger(1);

    DaemonThreadFactory(String namePrefix) {
      this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
      t.setDaemon(true);
      return t;
    }
  }
}
