package io.pravah.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.proto.runner.JobAssignment;
import io.pravah.proto.runner.JobSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves {@code secret_environment} via runner-service using the runner stream token. */
public class JobEnvironmentResolver {

  private static final Logger log = LoggerFactory.getLogger(JobEnvironmentResolver.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String runnerServiceHttpBaseUrl;
  private final UUID runnerId;
  private final String streamToken;
  private final OkHttpClient httpClient = new OkHttpClient();

  public JobEnvironmentResolver(
      String runnerServiceHttpBaseUrl, UUID runnerId, String streamToken) {
    this.runnerServiceHttpBaseUrl = stripTrailingSlash(runnerServiceHttpBaseUrl);
    this.runnerId = runnerId;
    this.streamToken = streamToken;
  }

  public JobAssignment mergeSecrets(JobAssignment assignment) {
    JobSpec spec = assignment.getSpec();
    Map<String, String> secretEnvironment = spec.getSecretEnvironmentMap();
    if (secretEnvironment.isEmpty()) {
      return assignment;
    }
    if (streamToken == null || streamToken.isBlank()) {
      throw new IllegalStateException("Runner stream token is required for secret resolution");
    }
    if (runnerServiceHttpBaseUrl == null || runnerServiceHttpBaseUrl.isBlank()) {
      throw new IllegalStateException("Runner service HTTP URL is required for secret resolution");
    }
    try {
      Map<String, String> resolved =
          resolveSecrets(
              UUID.fromString(assignment.getRunId()),
              UUID.fromString(assignment.getJobId()),
              secretEnvironment);
      for (String envKey : secretEnvironment.keySet()) {
        if (!resolved.containsKey(envKey) || resolved.get(envKey) == null) {
          throw new IllegalStateException("Missing resolved secret for environment key: " + envKey);
        }
      }
      Map<String, String> merged = new LinkedHashMap<>(spec.getEnvironmentMap());
      merged.putAll(resolved);
      JobSpec mergedSpec =
          spec.toBuilder()
              .clearEnvironment()
              .putAllEnvironment(merged)
              .clearSecretEnvironment()
              .build();
      return assignment.toBuilder().setSpec(mergedSpec).build();
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to resolve job environment secrets", e);
      throw new IllegalStateException("Failed to resolve job environment secrets", e);
    }
  }

  private Map<String, String> resolveSecrets(
      UUID executionId, UUID jobId, Map<String, String> secretEnvironment) throws Exception {
    String url =
        runnerServiceHttpBaseUrl
            + "/api/v1/runners/"
            + runnerId
            + "/jobs/"
            + jobId
            + "/environment-secrets";
    String body =
        MAPPER.writeValueAsString(
            Map.of("executionId", executionId.toString(), "secretEnvironment", secretEnvironment));
    Request request =
        new Request.Builder()
            .url(url)
            .post(RequestBody.create(body, JSON))
            .header("Authorization", "Bearer " + streamToken)
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new IllegalStateException("Secret resolution failed: HTTP " + response.code());
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = MAPPER.readValue(response.body().string(), Map.class);
      Object values = payload.get("values");
      if (!(values instanceof Map<?, ?> map)) {
        throw new IllegalStateException("Secret resolution returned empty values");
      }
      Map<String, String> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        result.put(entry.getKey().toString(), entry.getValue().toString());
      }
      return result;
    }
  }

  private static String stripTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
