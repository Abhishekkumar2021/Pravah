package io.pravah.runner;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.UnknownHostException;
import java.util.Map;
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
      description = "Authentication token for registration",
      required = true)
  private String token;

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

  public static void main(String[] args) {
    int exitCode = new CommandLine(new RunnerMain()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    String resolvedName = name != null ? name : getDefaultName();
    ParsedServer parsed = parseServerUrl(serverUrl);
    Map<String, String> labelMap = parseLabels(labels);

    log.info(
        "Starting Pravah Runner",
        kv("serverUrl", serverUrl),
        kv("runnerName", resolvedName),
        kv("maxJobs", maxJobs),
        kv("workDir", workDir));

    try (RunnerAgent agent =
        new RunnerAgent(
            parsed.host(), parsed.port(), token, resolvedName, labelMap, maxJobs, workDir)) {
      agent.start();
      Runtime.getRuntime().addShutdownHook(new Thread(agent::close));
      agent.awaitTermination();
    } catch (Exception e) {
      log.error("Runner failed", e);
      return 1;
    }
    return 0;
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
