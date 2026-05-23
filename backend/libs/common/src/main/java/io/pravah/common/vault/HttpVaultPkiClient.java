package io.pravah.common.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Issues X.509 certificates from Vault PKI (ADR-007). Used for runner client mTLS certs (ADR-008).
 */
public final class HttpVaultPkiClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final VaultSettings settings;
  private final HttpClient httpClient;
  private final VaultHttpTokenResolver tokenResolver;

  public HttpVaultPkiClient(VaultSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.httpClient = HttpClient.newBuilder().connectTimeout(settings.requestTimeout()).build();
    this.tokenResolver = new VaultHttpTokenResolver(settings, httpClient);
  }

  /**
   * Issues a leaf certificate from {@code issuePath} (e.g. {@code pki/issue/runner}).
   *
   * @param commonName CN for the certificate subject
   * @param uriSans SPIFFE URI SAN entries (at least one for runner identity)
   * @param ttl Vault TTL string (e.g. {@code 168h})
   */
  public VaultIssuedCertificate issue(
      String issuePath, String commonName, List<String> uriSans, String ttl) {
    Objects.requireNonNull(issuePath, "issuePath");
    Objects.requireNonNull(commonName, "commonName");
    Objects.requireNonNull(uriSans, "uriSans");
    Objects.requireNonNull(ttl, "ttl");
    if (issuePath.isBlank()) {
      throw new VaultException("Vault PKI issue path must not be blank");
    }
    if (issuePath.contains("..")) {
      throw new VaultException("Vault PKI issue path must not contain '..'");
    }
    if (commonName.isBlank()) {
      throw new VaultException("common_name must not be blank");
    }
    if (uriSans.isEmpty()) {
      throw new VaultException("At least one uri_sans entry is required for runner PKI");
    }

    String normalizedPath = issuePath.startsWith("/") ? issuePath.substring(1) : issuePath;
    URI uri = URI.create(trimTrailingSlash(settings.address()) + "/v1/" + normalizedPath);

    try {
      ObjectNode body = MAPPER.createObjectNode();
      body.put("common_name", commonName);
      body.put("ttl", ttl);
      ArrayNode sans = body.putArray("uri_sans");
      uriSans.forEach(sans::add);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(settings.requestTimeout())
              .header("X-Vault-Token", tokenResolver.resolveToken())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 300) {
        throw new VaultException(
            "Vault PKI issue failed (HTTP %d) for path %s: %s"
                .formatted(response.statusCode(), issuePath, sanitizeBody(response.body())));
      }
      JsonNode data = MAPPER.readTree(response.body()).path("data");
      String cert = textOrNull(data, "certificate");
      String key = textOrNull(data, "private_key");
      String ca = textOrNull(data, "issuing_ca");
      if (cert == null || key == null) {
        throw new VaultException("Vault PKI issue response missing certificate or private_key");
      }
      return new VaultIssuedCertificate(cert, key, ca != null ? ca : "");
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault PKI issue interrupted for path: " + issuePath, e);
    } catch (IOException e) {
      throw new VaultException("Vault PKI issue failed for path: " + issuePath, e);
    }
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    return value.asText();
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
