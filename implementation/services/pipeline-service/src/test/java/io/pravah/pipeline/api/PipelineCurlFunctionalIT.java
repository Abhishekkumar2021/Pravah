package io.pravah.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.ValidatePipelineRequest;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
class PipelineCurlFunctionalIT extends AbstractPipelinePostgresIT {

  private record CurlResult(int httpCode, String body) {}

  @LocalServerPort private int port;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Autowired private ObjectMapper objectMapper;

  private static boolean curlAvailable() {
    try {
      Process p = new ProcessBuilder("curl", "--version").redirectErrorStream(true).start();
      boolean finished = p.waitFor(5, TimeUnit.SECONDS);
      return finished && p.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private CurlResult curlRequest(List<String> trailingArgs)
      throws IOException, InterruptedException {
    Path bodyFile = Files.createTempFile("curl-body", ".json");
    try {
      List<String> cmd = new ArrayList<>();
      cmd.add("curl");
      cmd.add("-sS");
      cmd.add("-o");
      cmd.add(bodyFile.toAbsolutePath().toString());
      cmd.add("-w");
      cmd.add("%{http_code}");
      cmd.addAll(trailingArgs);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      byte[] stdout = p.getInputStream().readAllBytes();
      boolean finished = p.waitFor(120, TimeUnit.SECONDS);
      assertThat(finished).as("curl should finish within timeout").isTrue();
      String merged = new String(stdout, StandardCharsets.UTF_8).trim();
      if (p.exitValue() != 0) {
        throw new IllegalStateException("curl failed exit=" + p.exitValue() + " output=" + merged);
      }
      int httpCode = Integer.parseInt(merged);
      String body = Files.readString(bodyFile);
      return new CurlResult(httpCode, body);
    } finally {
      Files.deleteIfExists(bodyFile);
    }
  }

  @Test
  void actuatorHealth_reachableWithoutToken() throws Exception {
    Assumptions.assumeTrue(curlAvailable(), "curl executable not available");

    CurlResult r = curlRequest(List.of("http://localhost:" + port + "/actuator/health"));

    assertThat(r.httpCode()).isEqualTo(200);
    assertThat(r.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void curl_createsPipeline_validateAndGetDetail() throws Exception {
    Assumptions.assumeTrue(curlAvailable(), "curl executable not available");

    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    CreatePipelineRequest createReq =
        new CreatePipelineRequest(projectId, "curl-pipeline", null, "key: value\n");
    Path createPayload = Files.createTempFile("curl-create", ".json");
    Files.writeString(createPayload, objectMapper.writeValueAsString(createReq));
    try {
      CurlResult created =
          curlRequest(
              List.of(
                  "-X",
                  "POST",
                  "http://localhost:" + port + "/api/v1/pipelines",
                  "-H",
                  "Authorization: Bearer " + token,
                  "-H",
                  "Content-Type: application/json",
                  "--data-binary",
                  "@" + createPayload.toAbsolutePath()));

      assertThat(created.httpCode()).isEqualTo(201);
      JsonNode createdJson = objectMapper.readTree(created.body());
      UUID pipelineId = UUID.fromString(createdJson.get("id").asText());

      Path validatePayload = Files.createTempFile("curl-validate", ".json");
      Files.writeString(
          validatePayload,
          objectMapper.writeValueAsString(new ValidatePipelineRequest("stages: []\n")));
      try {
        CurlResult validated =
            curlRequest(
                List.of(
                    "-X",
                    "POST",
                    "http://localhost:" + port + "/api/v1/pipelines/validate",
                    "-H",
                    "Authorization: Bearer " + token,
                    "-H",
                    "Content-Type: application/json",
                    "--data-binary",
                    "@" + validatePayload.toAbsolutePath()));
        assertThat(validated.httpCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(validated.body()).get("valid").asBoolean()).isTrue();
      } finally {
        Files.deleteIfExists(validatePayload);
      }

      CurlResult detail =
          curlRequest(
              List.of(
                  "-H",
                  "Authorization: Bearer " + token,
                  "http://localhost:" + port + "/api/v1/pipelines/" + pipelineId));

      assertThat(detail.httpCode()).isEqualTo(200);
      assertThat(detail.body()).contains("\"name\":\"curl-pipeline\"");
      assertThat(detail.body()).contains("\"status\":\"draft\"");
    } finally {
      Files.deleteIfExists(createPayload);
    }
  }
}
