package io.pravah.common.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Vault Transit engine client for encrypt/decrypt (ADR-007). Keys never leave Vault. */
public final class HttpVaultTransitClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final VaultSettings settings;
  private final HttpClient httpClient;
  private final VaultHttpTokenResolver tokenResolver;
  private final String mountPath;

  public HttpVaultTransitClient(VaultSettings settings) {
    this(settings, "transit");
  }

  public HttpVaultTransitClient(VaultSettings settings, String mountPath) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.mountPath = normalizeMountPath(mountPath);
    this.httpClient = HttpClient.newBuilder().connectTimeout(settings.requestTimeout()).build();
    this.tokenResolver = new VaultHttpTokenResolver(settings, httpClient);
  }

  public void ensureTenantKey(java.util.UUID tenantId) {
    VaultTransitBootstrap.ensureKey(
        settings.address(),
        tokenResolver.resolveToken(),
        mountPath,
        VaultTransitKeyNames.tenantKey(tenantId));
  }

  public String encrypt(String keyName, String plaintext) {
    Objects.requireNonNull(keyName, "keyName");
    Objects.requireNonNull(plaintext, "plaintext");
    ObjectNode body = MAPPER.createObjectNode();
    body.put(
        "plaintext",
        Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8)));
    JsonNode data = post("/encrypt/" + keyName, body).path("data");
    JsonNode ciphertext = data.get("ciphertext");
    if (ciphertext == null || ciphertext.isNull()) {
      throw new VaultException("Vault Transit encrypt missing ciphertext for key: " + keyName);
    }
    return ciphertext.asText();
  }

  public String decrypt(String keyName, String ciphertext) {
    Objects.requireNonNull(keyName, "keyName");
    Objects.requireNonNull(ciphertext, "ciphertext");
    ObjectNode body = MAPPER.createObjectNode();
    body.put("ciphertext", ciphertext);
    JsonNode data = post("/decrypt/" + keyName, body).path("data");
    JsonNode plaintextB64 = data.get("plaintext");
    if (plaintextB64 == null || plaintextB64.isNull()) {
      throw new VaultException("Vault Transit decrypt missing plaintext for key: " + keyName);
    }
    return new String(Base64.getDecoder().decode(plaintextB64.asText()), StandardCharsets.UTF_8);
  }

  private JsonNode post(String operationPath, ObjectNode body) {
    String normalizedPath =
        operationPath.startsWith("/") ? operationPath.substring(1) : operationPath;
    URI uri =
        URI.create(
            trimTrailingSlash(settings.address()) + "/v1/" + mountPath + "/" + normalizedPath);
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(settings.requestTimeout())
              .header("X-Vault-Token", tokenResolver.resolveToken())
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 300) {
        throw new VaultException(
            "Vault Transit request failed (HTTP %d) %s: %s"
                .formatted(response.statusCode(), operationPath, sanitizeBody(response.body())));
      }
      return MAPPER.readTree(response.body());
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault Transit request interrupted: " + operationPath, e);
    } catch (IOException e) {
      throw new VaultException("Vault Transit request failed: " + operationPath, e);
    }
  }

  private static String normalizeMountPath(String mountPath) {
    if (mountPath == null || mountPath.isBlank()) {
      return "transit";
    }
    String trimmed =
        mountPath.endsWith("/") ? mountPath.substring(0, mountPath.length() - 1) : mountPath;
    return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
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
