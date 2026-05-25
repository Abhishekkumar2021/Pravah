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

/** Resolves Vault API tokens for token or Kubernetes auth (shared by KV/PKI HTTP clients). */
public final class VaultHttpTokenResolver {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final VaultSettings settings;
  private final HttpClient httpClient;
  private final Object tokenLock = new Object();
  private volatile String cachedToken;
  private volatile Instant tokenExpiresAt = Instant.EPOCH;

  public VaultHttpTokenResolver(VaultSettings settings, HttpClient httpClient) {
    this.settings = settings;
    this.httpClient = httpClient;
  }

  public String resolveToken() {
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
