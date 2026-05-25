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
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpVaultTransitClientTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/transit/encrypt/tenant-key",
        exchange -> {
          if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
          }
          writeJson(
              exchange,
              200,
              """
              {"data":{"ciphertext":"vault:v1:Zm9v","key_version":1}}
              """);
        });
    server.createContext(
        "/v1/transit/decrypt/tenant-key",
        exchange -> {
          if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
          }
          String plaintextB64 =
              Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
          writeJson(
              exchange,
              200,
              """
              {"data":{"plaintext":"%s"}}
              """
                  .formatted(plaintextB64));
        });
    server.createContext(
        "/v1/transit/keys/tenant-11111111-1111-1111-1111-111111111111",
        exchange -> {
          if ("GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(404, -1);
            return;
          }
          if ("POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 204, "");
            return;
          }
          exchange.sendResponseHeaders(405, -1);
        });
    server.createContext(
        "/v1/sys/mounts/transit",
        exchange -> {
          if ("POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 204, "");
            return;
          }
          exchange.sendResponseHeaders(405, -1);
        });
    server.createContext(
        "/v1/sys/health",
        exchange -> writeJson(exchange, 200, "{\"initialized\":true,\"sealed\":false}"));
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
  void encryptAndDecrypt_roundTrip() {
    VaultSettings settings = testSettings();
    HttpVaultTransitClient client = new HttpVaultTransitClient(settings);

    assertThat(client.encrypt("tenant-key", "hello")).isEqualTo("vault:v1:Zm9v");
    assertThat(client.decrypt("tenant-key", "vault:v1:Zm9v")).isEqualTo("hello");
  }

  @Test
  void ensureTenantKey_createsPerTenantKey() {
    VaultSettings settings = testSettings();
    HttpVaultTransitClient client = new HttpVaultTransitClient(settings);
    UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    client.ensureTenantKey(tenantId);
  }

  @Test
  void decrypt_missingPlaintext_throws() throws IOException {
    server.createContext(
        "/v1/transit/decrypt/bad-key", exchange -> writeJson(exchange, 200, "{\"data\":{}}"));
    VaultSettings settings = testSettings();
    HttpVaultTransitClient client = new HttpVaultTransitClient(settings);

    assertThatThrownBy(() -> client.decrypt("bad-key", "vault:v1:x"))
        .isInstanceOf(VaultException.class)
        .hasMessageContaining("missing plaintext");
  }

  private VaultSettings testSettings() {
    return new VaultSettings(
        true,
        baseUrl,
        "token",
        "dev-token",
        "",
        "kubernetes",
        "/var/run/secrets/kubernetes.io/serviceaccount/token",
        Duration.ofSeconds(5));
  }

  private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    }
  }
}
