package io.pravah.common.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Bootstraps Vault PKI for runner client mTLS certificates (ADR-007/008).
 *
 * <p>Idempotent HTTP setup: enable {@code pki} mount, generate internal root if missing, configure
 * {@code runner} role with SPIFFE URI SAN templates.
 */
public final class VaultPkiBootstrap {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private VaultPkiBootstrap() {}

  public static void bootstrapRunnerPki(
      String vaultAddress,
      String token,
      String pkiMount,
      String runnerRole,
      String trustDomain,
      String ttl) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String base = normalizeBase(vaultAddress);
    awaitVault(client, base, token);

    postIgnoreExists(client, base + "/v1/sys/mounts/" + pkiMount, token, "{\"type\":\"pki\"}");
    postExpectSuccess(
        client,
        base + "/v1/sys/mounts/" + pkiMount + "/tune",
        token,
        "{\"max_lease_ttl\":\"87600h\"}");

    if (!hasCaCertificate(client, base, token, pkiMount)) {
      postExpectSuccess(
          client,
          base + "/v1/" + pkiMount + "/root/generate/internal",
          token,
          "{\"common_name\":\"Pravah Runner PKI Root\",\"ttl\":\"87600h\"}");
    }

    String allowedUriSans =
        "spiffe://%s/tenant/*,spiffe://%s/runner/*".formatted(trustDomain, trustDomain);
    String roleJson =
        """
        {
          "allowed_uri_sans": "%s",
          "allow_any_name": true,
          "allow_subdomains": true,
          "key_type": "rsa",
          "key_bits": 4096,
          "ttl": "%s",
          "max_ttl": "%s",
          "require_cn": false,
          "use_csr_sans": true,
          "use_csr_common_name": true
        }
        """
            .formatted(allowedUriSans, ttl, ttl);
    postExpectSuccess(client, base + "/v1/" + pkiMount + "/roles/" + runnerRole, token, roleJson);
  }

  public static String readCaPem(String vaultAddress, String token, String pkiMount) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String base = normalizeBase(vaultAddress);
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(base + "/v1/" + pkiMount + "/cert/ca"))
              .timeout(Duration.ofSeconds(10))
              .header("X-Vault-Token", token)
              .GET()
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 300) {
        throw new VaultException(
            "Failed to read Vault PKI CA (HTTP %d)".formatted(response.statusCode()));
      }
      var root = MAPPER.readTree(response.body());
      String cert = root.path("data").path("certificate").asText(null);
      if (cert == null || cert.isBlank()) {
        cert = root.path("certificate").asText(null);
      }
      if (cert == null || cert.isBlank()) {
        throw new VaultException("Vault PKI CA response missing certificate field");
      }
      return cert;
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Interrupted reading Vault PKI CA", e);
    } catch (Exception e) {
      throw new VaultException("Failed to read Vault PKI CA", e);
    }
  }

  private static boolean hasCaCertificate(
      HttpClient client, String base, String token, String pkiMount) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(base + "/v1/" + pkiMount + "/cert/ca"))
              .timeout(Duration.ofSeconds(5))
              .header("X-Vault-Token", token)
              .GET()
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return response.statusCode() < 300;
    } catch (Exception e) {
      return false;
    }
  }

  private static void awaitVault(HttpClient client, String base, String token) {
    URI health = URI.create(base + "/v1/sys/health?standbyok=true");
    for (int i = 0; i < 60; i++) {
      try {
        HttpRequest req =
            HttpRequest.newBuilder()
                .uri(health)
                .timeout(Duration.ofSeconds(2))
                .header("X-Vault-Token", token)
                .GET()
                .build();
        HttpResponse<String> res =
            client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 200 || res.statusCode() == 429) {
          return;
        }
      } catch (Exception ignored) {
        // retry
      }
      sleepQuietly(500);
    }
    throw new VaultException("Vault did not become ready at " + base);
  }

  private static void postIgnoreExists(HttpClient client, String url, String token, String json) {
    HttpResponse<String> res = sendPost(client, url, token, json);
    if (res.statusCode() == 200 || res.statusCode() == 204) {
      return;
    }
    if (res.statusCode() == 400
        && res.body() != null
        && res.body().contains("path is already in use")) {
      return;
    }
    throw new VaultException(
        "Vault POST failed " + res.statusCode() + " " + url + ": " + truncate(res.body()));
  }

  private static void postExpectSuccess(HttpClient client, String url, String token, String json) {
    HttpResponse<String> res = sendPost(client, url, token, json);
    if (res.statusCode() >= 300) {
      throw new VaultException(
          "Vault POST failed " + res.statusCode() + " " + url + ": " + truncate(res.body()));
    }
  }

  private static HttpResponse<String> sendPost(
      HttpClient client, String url, String token, String json) {
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(120))
              .header("Content-Type", "application/json")
              .header("X-Vault-Token", token)
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault POST interrupted: " + url, e);
    } catch (Exception e) {
      throw new VaultException("Vault POST failed: " + url, e);
    }
  }

  private static String normalizeBase(String vaultAddress) {
    return vaultAddress.endsWith("/")
        ? vaultAddress.substring(0, vaultAddress.length() - 1)
        : vaultAddress;
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Interrupted", e);
    }
  }

  private static String truncate(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    return body.length() > 256 ? body.substring(0, 256) + "…" : body;
  }
}
