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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpVaultPkiClientTest {

  private HttpServer server;
  private String baseUrl;
  private String lastIssueBody;

  @BeforeEach
  void setUp() throws IOException {
    lastIssueBody = null;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/pki/issue/runner",
        exchange -> {
          if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
          }
          lastIssueBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          writeJson(
              exchange,
              200,
              """
              {"data":{"certificate":"-----BEGIN CERTIFICATE-----\\ncert\\n-----END CERTIFICATE-----",
              "private_key":"-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
              "issuing_ca":"-----BEGIN CERTIFICATE-----\\nca\\n-----END CERTIFICATE-----"}}
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
  void issue_returnsPemBundleWithSpiffeUri() {
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
    HttpVaultPkiClient client = new HttpVaultPkiClient(settings);

    VaultIssuedCertificate issued =
        client.issue(
            "pki/issue/runner",
            "22222222-2222-4222-8222-222222222222",
            List.of(
                "spiffe://pravah.local/tenant/11111111-1111-4111-8111-111111111111/runner/22222222-2222-4222-8222-222222222222"),
            "168h");

    assertThat(issued.certificatePem()).contains("BEGIN CERTIFICATE");
    assertThat(issued.privateKeyPem()).contains("PRIVATE KEY");
    assertThat(issued.issuingCaPem()).contains("BEGIN CERTIFICATE");
    assertThat(lastIssueBody).contains("uri_sans");
    assertThat(lastIssueBody).contains("spiffe://pravah.local/tenant/");
  }

  @Test
  void issue_rejectsBlankUriSans() {
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
    HttpVaultPkiClient client = new HttpVaultPkiClient(settings);

    assertThatThrownBy(() -> client.issue("pki/issue/runner", "cn", List.of(), "1h"))
        .isInstanceOf(VaultException.class)
        .hasMessageContaining("uri_sans");
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
