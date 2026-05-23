package io.pravah.common.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Production Vault KV v2 client using the JDK HTTP stack (no Spring dependency).
 *
 * <p>Supports token auth (local/dev) and Kubernetes auth (production pods).
 */
public final class HttpVaultKvClient implements VaultKvReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final VaultSettings settings;
  private final HttpClient httpClient;
  private final Object tokenLock = new Object();
  private volatile String cachedToken;
  private volatile Instant tokenExpiresAt = Instant.EPOCH;

  public HttpVaultKvClient(VaultSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.httpClient = HttpClient.newBuilder().connectTimeout(settings.requestTimeout()).build();
  }

  @Override
  public String readField(String apiPath, String fieldKey) {
    Objects.requireNonNull(apiPath, "apiPath");
    Objects.requireNonNull(fieldKey, "fieldKey");
    if (apiPath.isBlank()) {
      throw new VaultException("Vault path must not be blank");
    }
    if (fieldKey.isBlank()) {
      throw new VaultException("Vault field key must not be blank");
    }

    String normalizedPath = apiPath.startsWith("/") ? apiPath.substring(1) : apiPath;
    URI uri = URI.create(trimTrailingSlash(settings.address()) + "/v1/" + normalizedPath);

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(settings.requestTimeout())
              .header("X-Vault-Token", resolveToken())
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 404) {
        throw new VaultException("Vault secret not found at path: " + apiPath);
      }
      if (response.statusCode() >= 300) {
        throw new VaultException(
            "Vault read failed (HTTP %d) for path %s: %s"
                .formatted(response.statusCode(), apiPath, sanitizeBody(response.body())));
      }
      JsonNode root = MAPPER.readTree(response.body());
      JsonNode dataNode = root.path("data").path("data");
      if (dataNode.isMissingNode() || !dataNode.isObject()) {
        throw new VaultException("Vault response missing data.data for path: " + apiPath);
      }
      JsonNode field = dataNode.get(fieldKey);
      if (field == null || field.isNull()) {
        throw new VaultException(
            "Vault secret '%s' has no field '%s'".formatted(apiPath, fieldKey));
      }
      if (!field.isValueNode()) {
        throw new VaultException(
            "Vault field '%s' in '%s' is not a scalar value".formatted(fieldKey, apiPath));
      }
      return field.asText();
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault read interrupted for path: " + apiPath, e);
    } catch (IOException e) {
      throw new VaultException("Vault read failed for path: " + apiPath, e);
    }
  }

  private String resolveToken() {
    if (!settings.usesKubernetesAuth()) {
      if (settings.token() == null || settings.token().isBlank()) {
        throw new VaultException("VAULT_TOKEN is required when pravah.vault.auth.method=token");
      }
      return settings.token();
    }
    Instant now = Instant.now();
    if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(30))) {
      return cachedToken;
    }
    synchronized (tokenLock) {
      now = Instant.now();
      if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(30))) {
        return cachedToken;
      }
      cachedToken = loginWithKubernetes();
      return cachedToken;
    }
  }

  private String loginWithKubernetes() {
    if (settings.kubernetesRole() == null || settings.kubernetesRole().isBlank()) {
      throw new VaultException("PRAVAH_VAULT_K8S_ROLE is required for Kubernetes Vault auth");
    }
    try {
      String jwt =
          Files.readString(Path.of(settings.serviceAccountTokenPath()), StandardCharsets.UTF_8)
              .trim();
      String mount =
          settings.kubernetesMountPath().startsWith("/")
              ? settings.kubernetesMountPath().substring(1)
              : settings.kubernetesMountPath();
      URI loginUri =
          URI.create(trimTrailingSlash(settings.address()) + "/v1/auth/" + mount + "/login");
      String body =
          MAPPER.writeValueAsString(
              MAPPER.createObjectNode().put("role", settings.kubernetesRole()).put("jwt", jwt));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(loginUri)
              .timeout(settings.requestTimeout())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 300) {
        throw new VaultException(
            "Vault Kubernetes login failed (HTTP %d): %s"
                .formatted(response.statusCode(), sanitizeBody(response.body())));
      }
      JsonNode auth = MAPPER.readTree(response.body()).path("auth");
      String clientToken = auth.path("client_token").asText(null);
      if (clientToken == null || clientToken.isBlank()) {
        throw new VaultException("Vault Kubernetes login returned no client_token");
      }
      long leaseSeconds = auth.path("lease_duration").asLong(300);
      tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, leaseSeconds));
      return clientToken;
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault Kubernetes login interrupted", e);
    } catch (IOException e) {
      throw new VaultException("Vault Kubernetes login failed", e);
    }
  }

  private static String trimTrailingSlash(String address) {
    return address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
  }

  private static String sanitizeBody(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    return body.length() > 256 ? body.substring(0, 256) + "…" : body;
  }
}
