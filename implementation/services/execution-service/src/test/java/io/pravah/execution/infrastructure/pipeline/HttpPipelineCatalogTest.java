package io.pravah.execution.infrastructure.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpPipelineCatalogTest {

  private static final String BASE = "http://pipeline-service.test";

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private MockRestServiceServer mockServer;
  private HttpPipelineCatalog catalog;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    catalog = new HttpPipelineCatalog(builder, BASE);
  }

  @AfterEach
  void tearDown() {
    mockServer.reset();
  }

  @Test
  void resolve_nullVersion_usesCurrentVersionFromSummary() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    Instant t = Instant.parse("2026-05-14T12:00:00Z");

    Map<String, Object> summary =
        Map.of(
            "id",
            pipelineId,
            "projectId",
            projectId,
            "name",
            "daily",
            "currentVersion",
            2,
            "status",
            "active",
            "createdAt",
            t,
            "updatedAt",
            t);

    Map<String, Object> definition = Map.of("stages", List.of(Map.of("id", "a", "name", "A")));

    Map<String, Object> versionBody =
        Map.of(
            "pipelineId",
            pipelineId,
            "version",
            2,
            "definition",
            definition,
            "publishedAt",
            t,
            "publishedBy",
            UUID.randomUUID());

    mockServer
        .expect(requestTo(URI.create(BASE + "/api/v1/pipelines/" + pipelineId)))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(summary), MediaType.APPLICATION_JSON));

    mockServer
        .expect(requestTo(URI.create(BASE + "/api/v1/pipelines/" + pipelineId + "/versions/2")))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(versionBody), MediaType.APPLICATION_JSON));

    PublishedPipelineSnapshot snap = catalog.resolve(pipelineId, null, "Bearer unit-test-token");

    assertThat(snap.pipelineId()).isEqualTo(pipelineId);
    assertThat(snap.pipelineVersion()).isEqualTo(2);
    assertThat(snap.pipelineStatus()).isEqualTo("active");
    assertThat(snap.definition()).containsEntry("stages", definition.get("stages"));

    mockServer.verify();
  }

  @Test
  void resolve_explicitVersion_usesThatVersion() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    Instant t = Instant.parse("2026-05-14T12:00:00Z");

    Map<String, Object> summary =
        Map.of(
            "id",
            pipelineId,
            "projectId",
            UUID.randomUUID(),
            "name",
            "daily",
            "currentVersion",
            5,
            "status",
            "active",
            "createdAt",
            t,
            "updatedAt",
            t);

    Map<String, Object> versionBody =
        Map.of(
            "pipelineId",
            pipelineId,
            "version",
            1,
            "definition",
            Map.of("stages", List.of()),
            "publishedAt",
            t,
            "publishedBy",
            UUID.randomUUID());

    mockServer
        .expect(requestTo(URI.create(BASE + "/api/v1/pipelines/" + pipelineId)))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(summary), MediaType.APPLICATION_JSON));

    mockServer
        .expect(requestTo(URI.create(BASE + "/api/v1/pipelines/" + pipelineId + "/versions/1")))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(versionBody), MediaType.APPLICATION_JSON));

    PublishedPipelineSnapshot snap = catalog.resolve(pipelineId, 1, "Bearer unit-test-token");

    assertThat(snap.pipelineVersion()).isEqualTo(1);

    mockServer.verify();
  }

  @Test
  void resolve_versionNotFound_throwsEntityNotFound() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    Instant t = Instant.parse("2026-05-14T12:00:00Z");

    Map<String, Object> summary =
        Map.of(
            "id",
            pipelineId,
            "projectId",
            UUID.randomUUID(),
            "name",
            "daily",
            "currentVersion",
            1,
            "status",
            "active",
            "createdAt",
            t,
            "updatedAt",
            t);

    mockServer
        .expect(requestTo(URI.create(BASE + "/api/v1/pipelines/" + pipelineId)))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(summary), MediaType.APPLICATION_JSON));

    mockServer
        .expect(requestTo(URI.create(BASE + "/api/v1/pipelines/" + pipelineId + "/versions/1")))
        .andRespond(withResourceNotFound());

    assertThatThrownBy(() -> catalog.resolve(pipelineId, 1, "Bearer unit-test-token"))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("PipelineVersion");

    mockServer.verify();
  }
}
