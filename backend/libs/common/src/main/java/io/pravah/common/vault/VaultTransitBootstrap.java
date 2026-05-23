package io.pravah.common.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Bootstraps Vault Transit engine and per-tenant encryption keys (ADR-007). */
public final class VaultTransitBootstrap {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private VaultTransitBootstrap() {}

  public static void enableTransit(String vaultAddress, String token, String mountPath) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String base = normalizeBase(vaultAddress);
    String mount = normalizeMountPath(mountPath);
    awaitVault(client, base, token);
    postIgnoreExists(client, base + "/v1/sys/mounts/" + mount, token, "{\"type\":\"transit\"}");
  }

  public static void ensureKey(
      String vaultAddress, String token, String mountPath, String keyName) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String base = normalizeBase(vaultAddress);
    String mount = normalizeMountPath(mountPath);
    awaitVault(client, base, token);
    enableTransit(vaultAddress, token, mount);
    if (keyExists(client, base, token, mount, keyName)) {
      return;
    }
    String body =
        """
        {"type":"aes256-gcm96","exportable":false,"allow_plaintext_backup":false}
        """;
    postExpectSuccess(client, base + "/v1/" + mount + "/keys/" + keyName, token, body);
  }

  private static boolean keyExists(
      HttpClient client, String base, String token, String mount, String keyName) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(base + "/v1/" + mount + "/keys/" + keyName))
              .timeout(Duration.ofSeconds(10))
              .header("X-Vault-Token", token)
              .GET()
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return response.statusCode() == 200;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Interrupted while checking Transit key " + keyName, e);
    } catch (Exception e) {
      throw new VaultException("Failed to check Transit key " + keyName, e);
    }
  }

  private static void awaitVault(HttpClient client, String base, String token) {
    for (int attempt = 1; attempt <= 30; attempt++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(base + "/v1/sys/health?standbyok=true"))
                .timeout(Duration.ofSeconds(5))
                .header("X-Vault-Token", token)
                .GET()
                .build();
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 200) {
          return;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new VaultException("Interrupted while waiting for Vault", e);
      } catch (Exception ignored) {
        // retry
      }
      sleepQuietly(500L);
    }
    throw new VaultException("Vault did not become ready");
  }

  private static void postIgnoreExists(
      HttpClient client, String url, String token, String jsonBody) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .header("X-Vault-Token", token)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 204 || response.statusCode() < 300) {
        return;
      }
      if (response.statusCode() == 400 && response.body().contains("path is already in use")) {
        return;
      }
      throw new VaultException(
          "Vault POST %s failed (HTTP %d): %s"
              .formatted(url, response.statusCode(), response.body()));
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault POST interrupted: " + url, e);
    } catch (Exception e) {
      throw new VaultException("Vault POST failed: " + url, e);
    }
  }

  private static void postExpectSuccess(
      HttpClient client, String url, String token, String jsonBody) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .header("X-Vault-Token", token)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 300) {
        throw new VaultException(
            "Vault POST %s failed (HTTP %d): %s"
                .formatted(url, response.statusCode(), response.body()));
      }
    } catch (VaultException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Vault POST interrupted: " + url, e);
    } catch (Exception e) {
      throw new VaultException("Vault POST failed: " + url, e);
    }
  }

  private static String normalizeBase(String address) {
    return address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
  }

  private static String normalizeMountPath(String mountPath) {
    if (mountPath == null || mountPath.isBlank()) {
      return "transit";
    }
    String trimmed =
        mountPath.endsWith("/") ? mountPath.substring(0, mountPath.length() - 1) : mountPath;
    return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaultException("Interrupted", e);
    }
  }
}
