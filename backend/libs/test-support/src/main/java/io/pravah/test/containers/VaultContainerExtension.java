package io.pravah.test.containers;

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

  private static final GenericContainer<?> VAULT;

  static {
    VAULT =
        new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.15.6"))
            .withExposedPorts(8200)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
            .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withEnv("VAULT_ADDR", "http://127.0.0.1:8200")
            .withCommand("server", "-dev");
    VAULT.start();
    seedDemoSecret();
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

  private static void seedDemoSecret() {
    try {
      HttpClient client = HttpClient.newHttpClient();
      String body = "{\"data\":{\"password\":\"" + DEMO_PASSWORD + "\"}}";
      URI uri = URI.create(getAddress() + "/v1/" + DEMO_PATH);
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
        Thread.sleep(500L);
      }
      throw new IllegalStateException("Failed to seed Vault demo secret after retries");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while seeding Vault demo secret", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to seed Vault demo secret", e);
    }
  }

  private record VaultContainerResource(GenericContainer<?> container)
      implements CloseableResource {
    @Override
    public void close() {
      // Reusable container; Testcontainers manages lifecycle when reuse is enabled
    }
  }
}
