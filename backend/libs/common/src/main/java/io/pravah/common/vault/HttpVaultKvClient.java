package io.pravah.common.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
  private final VaultHttpTokenResolver tokenResolver;

  public HttpVaultKvClient(VaultSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.httpClient = HttpClient.newBuilder().connectTimeout(settings.requestTimeout()).build();
    this.tokenResolver = new VaultHttpTokenResolver(settings, httpClient);
  }

  @Override
  public String readField(String apiPath, String fieldKey) {
    Objects.requireNonNull(apiPath, "apiPath");
    Objects.requireNonNull(fieldKey, "fieldKey");
    if (apiPath.isBlank()) {
      throw new VaultException("Vault path must not be blank");
    }
    if (apiPath.contains("..")) {
      throw new VaultException("Vault path must not contain '..'");
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
              .header("X-Vault-Token", tokenResolver.resolveToken())
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
