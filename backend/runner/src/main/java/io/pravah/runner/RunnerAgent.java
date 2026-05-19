package io.pravah.runner;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.pravah.proto.runner.*;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
  private final String token;
  private final String name;
  private final Map<String, String> labels;
  private final int maxJobs;
  private final String workDir;

  private ManagedChannel channel;
  private RunnerServiceGrpc.RunnerServiceStub asyncStub;
  private RunnerServiceGrpc.RunnerServiceBlockingStub blockingStub;
  private ScheduledExecutorService scheduler;
  private java.util.concurrent.ExecutorService jobExecutor;
  private StreamObserver<RunnerMessage> requestObserver;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicInteger activeJobs = new AtomicInteger(0);
  private String runnerId;

  public RunnerAgent(
      String serverHost,
      int serverPort,
      String token,
      String name,
      Map<String, String> labels,
      int maxJobs,
      String workDir) {
    this.serverHost = serverHost;
    this.serverPort = serverPort;
    this.token = token;
    this.name = name;
    this.labels = labels;
    this.maxJobs = maxJobs;
    this.workDir = workDir;
  }

  public void start() throws InterruptedException {
    channel = ManagedChannelBuilder.forAddress(serverHost, serverPort).usePlaintext().build();
    asyncStub = RunnerServiceGrpc.newStub(channel);
    blockingStub = RunnerServiceGrpc.newBlockingStub(channel);

    register();
    connectStream();

    running.set(true);
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "runner-heartbeat"));
    jobExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "runner-job-worker"));
    scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 30, TimeUnit.SECONDS);

    log.info("Runner agent started", kv("runnerId", runnerId), kv("workDir", workDir));
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
                    .addSupportedExecutors("shell")
                    .setAvailableMemoryBytes(Runtime.getRuntime().maxMemory())
                    .setAvailableCpus(Runtime.getRuntime().availableProcessors())
                    .build());

    labels.forEach(
        (k, v) ->
            request.addLabels(
                io.pravah.proto.common.Label.newBuilder().setKey(k).setValue(v).build()));

    RegisterRunnerResponse response = blockingStub.registerRunner(request.build());
    runnerId = response.getRunnerId();
    log.info(
        "Registered with runner service",
        kv("runnerId", runnerId),
        kv("heartbeatInterval", response.getHeartbeatIntervalSeconds()));
  }

  private void connectStream() throws InterruptedException {
    CountDownLatch connected = new CountDownLatch(1);

    StreamObserver<ServerMessage> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(ServerMessage message) {
            if (message.hasJobAssignment()) {
              handleJobAssignment(message.getJobAssignment());
            } else if (message.hasJobCancellation()) {
              log.info(
                  "Job cancellation received",
                  kv("jobId", message.getJobCancellation().getJobId()));
            } else if (message.hasShutdown()) {
              log.warn("Shutdown requested", kv("reason", message.getShutdown().getReason()));
              running.set(false);
            }
          }

          @Override
          public void onError(Throwable t) {
            log.error("Runner stream error", t);
            running.set(false);
          }

          @Override
          public void onCompleted() {
            log.info("Runner stream completed");
            running.set(false);
          }
        };

    requestObserver = asyncStub.connect(responseObserver);
    connected.countDown();
    connected.await(5, TimeUnit.SECONDS);
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
            reportJobStatus(jobId, JobStatus.JOB_STATUS_RUNNING, 0, startedAt, 0, null);
            JobExecutor executor = new JobExecutor(workDir);
            int exitCode = executor.execute(assignment);
            reportJobStatus(
                jobId,
                exitCode == 0 ? JobStatus.JOB_STATUS_SUCCEEDED : JobStatus.JOB_STATUS_FAILED,
                exitCode,
                startedAt,
                System.currentTimeMillis(),
                null);
          } catch (Exception e) {
            log.error("Job execution failed", kv("jobId", jobId), e);
            reportJobStatus(
                jobId,
                JobStatus.JOB_STATUS_FAILED,
                1,
                startedAt,
                System.currentTimeMillis(),
                e.getMessage());
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
      String error) {
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
    requestObserver.onNext(RunnerMessage.newBuilder().setJobStatus(update.build()).build());
  }

  public void awaitTermination() throws InterruptedException {
    while (running.get()) {
      Thread.sleep(1000);
    }
  }

  @Override
  public void close() {
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
}
