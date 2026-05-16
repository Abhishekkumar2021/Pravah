package io.pravah.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.execution.api.dto.CreateExecutionRequest;
import io.pravah.execution.application.port.PipelineCatalog;
import io.pravah.execution.application.port.PublishedPipelineSnapshot;
import io.pravah.execution.infrastructure.realtime.ExecutionRealtimePayload;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Integration tests for US-12.10: browser WebSocket stream of {@code execution.updated} events. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfiguration.class, ExecutionManualRunIT.PipelineCatalogTestConfig.class})
class ExecutionWebSocketIT extends AbstractExecutionPostgresIT {

  private static final Duration WS_TIMEOUT = Duration.ofSeconds(15);

  @LocalServerPort private int port;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TestJwtIssuer testJwtIssuer;

  @Autowired private PipelineCatalog pipelineCatalog;

  @BeforeEach
  void resetPipelineCatalogMock() {
    reset(pipelineCatalog);
  }

  @Test
  void postManualExecution_pushesExecutionUpdatedOverWebSocket() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    String token = testJwtIssuer.generateAccessToken(userId, tenantId);

    when(pipelineCatalog.resolve(eq(pipelineId), isNull(), anyString()))
        .thenReturn(
            new PublishedPipelineSnapshot(
                pipelineId,
                1,
                Map.of("stages", List.of(Map.of("id", "extract", "name", "Extract data"))),
                "active"));

    CountDownLatch messageLatch = new CountDownLatch(1);
    AtomicReference<String> payloadRef = new AtomicReference<>();

    WebSocketSession wsSession = connectWebSocket(token, messageLatch, payloadRef, null);

    try {
      mockMvc
          .perform(
              post("/api/v1/executions")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new CreateExecutionRequest(pipelineId, null))))
          .andExpect(status().isCreated());

      assertThat(messageLatch.await(WS_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
          .as("execution.updated WebSocket frame after POST /executions")
          .isTrue();

      JsonNode frame = objectMapper.readTree(payloadRef.get());
      assertThat(frame.get("type").asText())
          .isEqualTo(ExecutionRealtimePayload.TYPE_EXECUTION_UPDATED);
      assertThat(frame.get("status").asText()).isEqualTo("pending");
      assertThat(frame.get("pipelineId").asText()).isEqualTo(pipelineId.toString());
      assertThat(frame.hasNonNull("executionId")).isTrue();
    } finally {
      wsSession.close();
    }
  }

  @Test
  void webSocketHandshake_withoutToken_rejected() {
    StandardWebSocketClient client = new StandardWebSocketClient();
    URI uri = URI.create("ws://localhost:" + port + "/ws/v1/executions");
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://localhost:5173");

    assertThatThrownBy(
            () ->
                client
                    .execute(new TextWebSocketHandler() {}, headers, uri)
                    .get(WS_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
        .rootCause()
        .hasMessageContaining("did not permit the HTTP upgrade to WebSocket");
  }

  private WebSocketSession connectWebSocket(
      String accessToken,
      CountDownLatch messageLatch,
      AtomicReference<String> payloadRef,
      UUID filterExecutionId)
      throws Exception {
    StandardWebSocketClient client = new StandardWebSocketClient();
    URI uri =
        URI.create(
            "ws://localhost:"
                + port
                + "/ws/v1/executions?access_token="
                + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8));

    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://localhost:5173");

    TextWebSocketHandler handler =
        new TextWebSocketHandler() {
          @Override
          protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
              JsonNode node = objectMapper.readTree(message.getPayload());
              if (!ExecutionRealtimePayload.TYPE_EXECUTION_UPDATED.equals(
                  node.path("type").asText(null))) {
                return;
              }
              if (filterExecutionId != null
                  && !filterExecutionId.toString().equals(node.path("executionId").asText())) {
                return;
              }
              payloadRef.set(message.getPayload());
              messageLatch.countDown();
            } catch (Exception ignored) {
              // ignore malformed frames in this IT
            }
          }
        };

    return client.execute(handler, headers, uri).get(WS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }
}
