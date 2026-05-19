package io.pravah.runnerservice.infrastructure.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.pravah.runnerservice.grpc.RunnerServiceGrpcImpl;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Starts the gRPC server for runner agent connections (port from {@code grpc.server.port}). */
@Component
public class RunnerGrpcServerLifecycle {

  private static final Logger log = LoggerFactory.getLogger(RunnerGrpcServerLifecycle.class);

  private final RunnerServiceGrpcImpl runnerServiceGrpc;
  private final int port;

  private Server server;

  public RunnerGrpcServerLifecycle(
      RunnerServiceGrpcImpl runnerServiceGrpc, @Value("${grpc.server.port:9091}") int port) {
    this.runnerServiceGrpc = runnerServiceGrpc;
    this.port = port;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() throws IOException {
    server = ServerBuilder.forPort(port).addService(runnerServiceGrpc).build().start();
    log.info("Runner gRPC server started on port {}", port);
  }

  @PreDestroy
  public void stop() throws InterruptedException {
    if (server == null) {
      return;
    }
    server.shutdown();
    if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
      server.shutdownNow();
    }
    log.info("Runner gRPC server stopped");
  }
}
