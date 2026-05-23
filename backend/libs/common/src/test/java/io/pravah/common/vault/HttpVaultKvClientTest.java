package io.pravah.common.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpVaultKvClientTest {

  private HttpServer server;
  private String baseUrl;
  private int kvReads;

  @BeforeEach
  void setUp() throws IOException {
    kvReads = 0;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/secret/data/myapp",
        exchange -> {
          if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
          }
          kvReads++;
          String token = exchange.getRequestHeaders().getFirst("X-Vault-Token");
          if (!"dev-token".equals(token)) {
            writeJson(exchange, 403, "{\"errors\":[\"permission denied\"]}");
            return;
          }
          writeJson(
              exchange,
              200,
              """
              {"data":{"data":{"password":"s3cret"},"metadata":{"version":1}}}
              """);
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void readField_returnsKvV2Value() {
    VaultSettings settings =
        new VaultSettings(
            true,
            baseUrl,
            "token",
            "dev-token",
            "",
            "kubernetes",
            "/var/run/secrets/kubernetes.io/serviceaccount/token",
            Duration.ofSeconds(5));
    HttpVaultKvClient client = new HttpVaultKvClient(settings);

    assertThat(client.readField("secret/data/myapp", "password")).isEqualTo("s3cret");
    assertThat(kvReads).isEqualTo(1);
  }

  @Test
  void readField_missingField_throws() {
    VaultSettings settings =
        new VaultSettings(
            true,
            baseUrl,
            "token",
            "dev-token",
            "",
            "kubernetes",
            "/var/run/secrets/kubernetes.io/serviceaccount/token",
            Duration.ofSeconds(5));
    HttpVaultKvClient client = new HttpVaultKvClient(settings);

    assertThatThrownBy(() -> client.readField("secret/data/myapp", "missing"))
        .isInstanceOf(VaultException.class)
        .hasMessageContaining("no field 'missing'");
  }

  private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
