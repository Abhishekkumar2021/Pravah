package io.pravah.runner;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.grpc.GrpcTlsConfig;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Pravah Runner - Standalone job execution agent.
 *
 * <p>Deployed in customer infrastructure to execute pipeline jobs. Connects to the Pravah control
 * plane via gRPC bidirectional streaming.
 *
 * <pre>
 * Usage: pravah-runner [OPTIONS]
 *
 * Options:
 *   --server-url    Runner Service gRPC endpoint
 *   --token         Authentication token
 *   --name          Runner name
 *   --labels        Key=value labels for job matching
 *   --config        Path to configuration file
 * </pre>
 *
 * @see <a href="../../../docs/lld/03-state-machines.md">State Machines - Runner States</a>
 */
@Command(
    name = "pravah-runner",
    mixinStandardHelpOptions = true,
    version = "Pravah Runner 0.1.0",
    description = "Pravah pipeline job execution agent")
public class RunnerMain implements Callable<Integer> {

  private static final Logger log = LoggerFactory.getLogger(RunnerMain.class);

  @Option(
      names = {"--server-url", "-s"},
      description = "Runner Service gRPC endpoint (e.g., runner.pravah.io:443)",
      required = true)
  private String serverUrl;

  @Option(
      names = {"--token", "-t"},
      description =
          "Runner stream token (from prior registration, or returned after first register)")
  private String token;

  @Option(
      names = {"--tenant-id"},
      description = "Tenant UUID for runner registration (required unless --runner-id is set)")
  private String tenantId;

  @Option(
      names = {"--bootstrap-secret"},
      description =
          "Bootstrap secret for registration (default: PRAVAH_RUNNER_BOOTSTRAP_SECRET env)")
  private String bootstrapSecret;

  @Option(
      names = {"--runner-id"},
      description = "Existing runner ID to reconnect without re-registering")
  private String runnerId;

  @Option(
      names = {"--name", "-n"},
      description = "Runner name (defaults to hostname)")
  private String name;

  @Option(
      names = {"--labels", "-l"},
      description = "Labels for job matching (key=value format, can be repeated)",
      split = ",")
  private String[] labels;

  @Option(
      names = {"--config", "-c"},
      description = "Path to configuration file")
  private String configPath;

  @Option(
      names = {"--max-jobs"},
      description = "Maximum concurrent jobs (default: 4)",
      defaultValue = "4")
  private int maxJobs;

  @Option(
      names = {"--work-dir"},
      description = "Working directory for job execution",
      defaultValue = "/tmp/pravah-runner")
  private String workDir;

  @Option(
      names = {"--runner-http-url"},
      description =
          "Runner Service HTTP base URL for secret resolution (env: PRAVAH_RUNNER_HTTP_URL)")
  private String runnerHttpUrl;

  @Option(
      names = {"--tls-enabled"},
      description = "Enable TLS for gRPC (mTLS when client cert/key are set)")
  private boolean tlsEnabled;

  @Option(
      names = {"--tls-trust-cert"},
      description = "CA or server certificate for gRPC TLS trust")
  private String tlsTrustCert;

  @Option(
      names = {"--tls-client-cert"},
      description = "Client certificate for gRPC mTLS")
  private String tlsClientCert;

  @Option(
      names = {"--tls-client-key"},
      description = "Client private key for gRPC mTLS")
  private String tlsClientKey;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new RunnerMain()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    try {
      validateConfiguration();
    } catch (IllegalArgumentException e) {
      log.error("Configuration error: {}", e.getMessage());
      return 1;
    }

    String resolvedName = name != null ? name : getDefaultName();
    ParsedServer parsed = parseServerUrl(serverUrl);
    Map<String, String> labelMap = parseLabels(labels);

    log.info(
        "Starting Pravah Runner",
        kv("serverUrl", serverUrl),
        kv("runnerName", resolvedName),
        kv("maxJobs", maxJobs),
        kv("workDir", workDir));

    UUID tenantUuid = resolveTenantId();
    String bootstrap = resolveBootstrapSecret();
    String runnerHttp = resolveRunnerHttpUrl();
    GrpcTlsConfig tlsConfig = resolveTlsConfig();
    try (RunnerAgent agent =
        new RunnerAgent(
            parsed.host(),
            parsed.port(),
            tenantUuid,
            bootstrap,
            token,
            runnerId,
            resolvedName,
            labelMap,
            maxJobs,
            workDir,
            tlsConfig,
            runnerHttp)) {
      agent.start();
      Runtime.getRuntime().addShutdownHook(new Thread(agent::close));
      agent.awaitTermination();
    } catch (Exception e) {
      log.error("Runner failed", e);
      return 1;
    }
    return 0;
  }

  private void validateConfiguration() {
    if (runnerId != null && !runnerId.isBlank()) {
      if (token == null || token.isBlank()) {
        throw new IllegalArgumentException(
            "--token is required when reconnecting with --runner-id");
      }
      try {
        UUID.fromString(runnerId);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("--runner-id must be a valid UUID");
      }
    }

    if (maxJobs < 1 || maxJobs > 100) {
      throw new IllegalArgumentException("--max-jobs must be between 1 and 100");
    }

    java.io.File workDirFile = new java.io.File(workDir);
    if (!workDirFile.exists() && !workDirFile.mkdirs()) {
      throw new IllegalArgumentException("Cannot create work directory: " + workDir);
    }
    if (!workDirFile.isDirectory() || !workDirFile.canWrite()) {
      throw new IllegalArgumentException("Work directory is not writable: " + workDir);
    }
  }

  private static Map<String, String> parseLabels(String[] labels) {
    Map<String, String> map = new java.util.LinkedHashMap<>();
    if (labels == null) {
      return map;
    }
    for (String label : labels) {
      int eq = label.indexOf('=');
      if (eq > 0) {
        map.put(label.substring(0, eq).trim(), label.substring(eq + 1).trim());
      }
    }
    return map;
  }

  private record ParsedServer(String host, int port) {}

  private static ParsedServer parseServerUrl(String url) {
    String normalized = url.replace("grpc://", "").replace("http://", "").replace("https://", "");
    int colon = normalized.lastIndexOf(':');
    if (colon > 0) {
      return new ParsedServer(
          normalized.substring(0, colon), Integer.parseInt(normalized.substring(colon + 1)));
    }
    return new ParsedServer(normalized, 9091);
  }

  private UUID resolveTenantId() {
    // Check CLI argument first
    if (tenantId != null && !tenantId.isBlank()) {
      return UUID.fromString(tenantId);
    }
    // Fall back to environment variable
    String env = System.getenv("PRAVAH_TENANT_ID");
    if (env != null && !env.isBlank()) {
      return UUID.fromString(env);
    }
    throw new IllegalArgumentException(
        "--tenant-id or PRAVAH_TENANT_ID is required for runner registration");
  }

  private String resolveBootstrapSecret() {
    if (bootstrapSecret != null && !bootstrapSecret.isBlank()) {
      return bootstrapSecret;
    }
    String env = System.getenv("PRAVAH_RUNNER_BOOTSTRAP_SECRET");
    if (env != null && !env.isBlank()) {
      return env;
    }
    throw new IllegalArgumentException(
        "--bootstrap-secret or PRAVAH_RUNNER_BOOTSTRAP_SECRET is required for runner registration");
  }

  private String resolveRunnerHttpUrl() {
    if (runnerHttpUrl != null && !runnerHttpUrl.isBlank()) {
      return runnerHttpUrl.trim();
    }
    String env = System.getenv("PRAVAH_RUNNER_HTTP_URL");
    if (env != null && !env.isBlank()) {
      return env.trim();
    }
    return "http://localhost:8086";
  }

  private GrpcTlsConfig resolveTlsConfig() {
    boolean enabled = tlsEnabled || isTruthyEnv("PRAVAH_RUNNER_GRPC_TLS_ENABLED");
    if (!enabled) {
      return GrpcTlsConfig.disabled();
    }
    String trust = firstNonBlank(tlsTrustCert, System.getenv("PRAVAH_RUNNER_GRPC_TLS_TRUST_CERT"));
    String clientCert =
        firstNonBlank(tlsClientCert, System.getenv("PRAVAH_RUNNER_GRPC_TLS_CLIENT_CERT"));
    String clientKey =
        firstNonBlank(tlsClientKey, System.getenv("PRAVAH_RUNNER_GRPC_TLS_CLIENT_KEY"));
    if (trust == null) {
      throw new IllegalArgumentException(
          "--tls-trust-cert or PRAVAH_RUNNER_GRPC_TLS_TRUST_CERT is required when TLS is enabled");
    }
    return GrpcTlsConfig.client(
        Path.of(trust),
        clientCert != null ? Path.of(clientCert) : null,
        clientKey != null ? Path.of(clientKey) : null);
  }

  private static boolean isTruthyEnv(String name) {
    String value = System.getenv(name);
    return value != null && (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String getDefaultName() {
    try {
      return java.net.InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      String fallbackName = "runner-" + System.currentTimeMillis();
      log.warn(
          "Failed to resolve hostname, using fallback name",
          kv("fallbackName", fallbackName),
          kv("error", e.getMessage()));
      return fallbackName;
    }
  }
}
