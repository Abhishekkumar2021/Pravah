package io.pravah.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pravah.proto.runner.JobAssignment;
import io.pravah.proto.runner.JobSpec;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobEnvironmentResolverTest {

  private MockWebServer server;
  private UUID runnerId;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    runnerId = UUID.randomUUID();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void mergeSecrets_resolvesViaRunnerServiceWithBearerToken() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"values\":{\"API_KEY\":\"resolved\"}}")
            .addHeader("Content-Type", "application/json"));

    JobAssignment assignment =
        JobAssignment.newBuilder()
            .setJobId(UUID.randomUUID().toString())
            .setRunId(UUID.randomUUID().toString())
            .setSpec(
                JobSpec.newBuilder()
                    .setExecutor("container")
                    .putSecretEnvironment("API_KEY", "api_key")
                    .build())
            .build();

    JobEnvironmentResolver resolver =
        new JobEnvironmentResolver(server.url("/").toString(), runnerId, "stream-token");
    JobAssignment merged = resolver.mergeSecrets(assignment);

    assertThat(merged.getSpec().getEnvironmentMap()).containsEntry("API_KEY", "resolved");
    assertThat(merged.getSpec().getSecretEnvironmentCount()).isZero();

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath())
        .contains("/api/v1/runners/" + runnerId + "/jobs/" + assignment.getJobId());
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer stream-token");
  }

  @Test
  void mergeSecrets_failsWhenResolutionMissingKey() {
    JobAssignment assignment =
        JobAssignment.newBuilder()
            .setJobId(UUID.randomUUID().toString())
            .setRunId(UUID.randomUUID().toString())
            .setSpec(
                JobSpec.newBuilder()
                    .setExecutor("container")
                    .putSecretEnvironment("API_KEY", "api_key")
                    .build())
            .build();

    JobEnvironmentResolver resolver =
        new JobEnvironmentResolver(server.url("/").toString(), runnerId, "stream-token");

    assertThatThrownBy(() -> resolver.mergeSecrets(assignment))
        .isInstanceOf(IllegalStateException.class);
  }
}
