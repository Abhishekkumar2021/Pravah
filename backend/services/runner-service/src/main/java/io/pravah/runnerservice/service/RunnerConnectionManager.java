package io.pravah.runnerservice.service;

import io.grpc.stub.StreamObserver;
import io.pravah.proto.runner.ServerMessage;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages active gRPC connections from runners. Used to send job assignments and cancellations to
 * specific runners.
 */
@Component
public class RunnerConnectionManager {

  private static final Logger log = LoggerFactory.getLogger(RunnerConnectionManager.class);

  private final Map<UUID, RunnerConnection> connections = new ConcurrentHashMap<>();

  /** Registers a new runner connection. */
  public void register(UUID runnerId, StreamObserver<ServerMessage> observer) {
    RunnerConnection existing = connections.put(runnerId, new RunnerConnection(runnerId, observer));
    if (existing != null) {
      log.warn("Runner {} reconnected, closing old connection", runnerId);
      existing.close();
    }
    log.info("Runner {} connected", runnerId);
  }

  /** Unregisters a runner connection. */
  public void unregister(UUID runnerId) {
    RunnerConnection removed = connections.remove(runnerId);
    if (removed != null) {
      log.info("Runner {} disconnected", runnerId);
    }
  }

  /** Gets a runner connection. */
  public Optional<RunnerConnection> getConnection(UUID runnerId) {
    return Optional.ofNullable(connections.get(runnerId));
  }

  /** Sends a message to a specific runner. */
  public boolean sendMessage(UUID runnerId, ServerMessage message) {
    RunnerConnection connection = connections.get(runnerId);
    if (connection != null) {
      return connection.send(message);
    }
    return false;
  }

  /** Returns the number of active connections. */
  public int getActiveConnectionCount() {
    return connections.size();
  }

  /** Checks if a runner is connected. */
  public boolean isConnected(UUID runnerId) {
    return connections.containsKey(runnerId);
  }

  /** Wrapper for a runner's gRPC stream. */
  public static class RunnerConnection {
    private final UUID runnerId;
    private final StreamObserver<ServerMessage> observer;
    private volatile boolean closed = false;

    RunnerConnection(UUID runnerId, StreamObserver<ServerMessage> observer) {
      this.runnerId = runnerId;
      this.observer = observer;
    }

    public UUID getRunnerId() {
      return runnerId;
    }

    public synchronized boolean send(ServerMessage message) {
      if (closed) {
        return false;
      }
      try {
        observer.onNext(message);
        return true;
      } catch (Exception e) {
        log.warn("Failed to send message to runner {}: {}", runnerId, e.getMessage());
        return false;
      }
    }

    public synchronized void close() {
      if (!closed) {
        closed = true;
        try {
          observer.onCompleted();
        } catch (Exception ignored) {
        }
      }
    }

    public synchronized void error(Throwable t) {
      if (!closed) {
        closed = true;
        try {
          observer.onError(t);
        } catch (Exception ignored) {
        }
      }
    }
  }
}
