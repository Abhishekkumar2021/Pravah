package io.pravah.test.containers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.vault.VaultPkiBootstrap;
import io.pravah.common.vault.VaultTransitBootstrap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit 5 extension providing a shared Vault dev container for integration tests (ADR-007).
 *
 * <p>Dev mode pre-mounts KV v2 at {@code secret/}. Seeds {@code secret/data/pravah/it-test} with
 * field {@code password} for credential resolution tests.
 */
public class VaultContainerExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Namespace NAMESPACE = Namespace.create(VaultContainerExtension.class);
  private static final String CONTAINER_KEY = "vault-container";
  private static final String ROOT_TOKEN = "dev-root-token";
  private static final String DEMO_PATH = "secret/data/pravah/it-test";
  private static final String DEMO_PASSWORD = "vault-it-password";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final GenericContainer<?> VAULT;

  static {
    VAULT =
        new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.15.6"))
            .withExposedPorts(8200)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
            .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withEnv("VAULT_ADDR", "http://127.0.0.1:8200")
            .withCommand("server", "-dev")
            .withReuse(true);
    VAULT.start();
    waitForVaultReady();
    seedDemoSecret();
    VaultPkiBootstrap.bootstrapRunnerPki(
        getAddress(), ROOT_TOKEN, "pki", "runner", "pravah.local", "168h");
    VaultTransitBootstrap.enableTransit(getAddress(), ROOT_TOKEN, "transit");
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    context
        .getRoot()
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            CONTAINER_KEY, key -> new VaultContainerResource(VAULT), VaultContainerResource.class);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Cleanup via CloseableResource when root store closes
  }

  public static String getAddress() {
    return "http://" + VAULT.getHost() + ":" + VAULT.getMappedPort(8200);
  }

  public static String getRootToken() {
    return ROOT_TOKEN;
  }

  public static String demoVaultReference() {
    return "vault:" + DEMO_PATH + "#password";
  }

  public static String demoPassword() {
    return DEMO_PASSWORD;
  }

  private static void waitForVaultReady() {
    HttpClient client = HttpClient.newHttpClient();
    URI healthUri = URI.create(getAddress() + "/v1/sys/health?standbyok=true");
    for (int attempt = 1; attempt <= 30; attempt++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder().uri(healthUri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 200) {
          return;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for Vault", e);
      } catch (Exception ignored) {
        // retry until Vault dev server is listening
      }
      sleepQuietly(500L);
    }
    throw new IllegalStateException("Vault container did not become ready within timeout");
  }

  private static void seedDemoSecret() {
    try {
      HttpClient client = HttpClient.newHttpClient();
      var root = MAPPER.createObjectNode();
      root.putObject("data").put("password", DEMO_PASSWORD);
      String body = MAPPER.writeValueAsString(root);
      URI uri = URI.create(getAddress() + "/v1/" + DEMO_PATH);
      int lastStatus = -1;
      String lastBody = "";
      for (int attempt = 1; attempt <= 30; attempt++) {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(5))
                .header("X-Vault-Token", ROOT_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 300) {
          return;
        }
        lastStatus = response.statusCode();
        lastBody = response.body();
        sleepQuietly(500L);
      }
      throw new IllegalStateException(
          "Failed to seed Vault demo secret after retries (HTTP %d): %s"
              .formatted(lastStatus, truncate(lastBody)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while seeding Vault demo secret", e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to seed Vault demo secret", e);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    }
  }

  private static String truncate(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    return body.length() > 256 ? body.substring(0, 256) + "…" : body;
  }

  private record VaultContainerResource(GenericContainer<?> container)
      implements CloseableResource {
    @Override
    public void close() {
      if (!container.isShouldBeReused() && container.isRunning()) {
        container.stop();
      }
    }
  }
}
