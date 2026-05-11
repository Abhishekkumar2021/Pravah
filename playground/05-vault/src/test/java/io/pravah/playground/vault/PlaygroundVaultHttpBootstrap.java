package io.pravah.playground.vault;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Enables Database + PKI engines over Vault's HTTP API so Testcontainers IT does not depend on the host
 * having the vault CLI installed.
 */
final class PlaygroundVaultHttpBootstrap {

    private PlaygroundVaultHttpBootstrap() {}

    static void bootstrap(String vaultBaseUrl, String token, String postgresConnectionUrlFromVaultContainer) {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        awaitVault(client, normalizeBase(vaultBaseUrl), token);

        postIgnoreExists(client, vaultBaseUrl + "/v1/sys/mounts/database", token, "{\"type\":\"database\"}");
        postIgnoreExists(client, vaultBaseUrl + "/v1/sys/mounts/pki", token, "{\"type\":\"pki\"}");

        postExpectSuccess(
                client,
                vaultBaseUrl + "/v1/sys/mounts/pki/tune",
                token,
                "{\"max_lease_ttl\":\"87600h\"}");

        postExpectSuccess(
                client,
                vaultBaseUrl + "/v1/pki/root/generate/internal",
                token,
                "{\"common_name\":\"Playground Root\",\"ttl\":\"87600h\"}");

        postExpectSuccess(
                client,
                vaultBaseUrl + "/v1/pki/roles/playground",
                token,
                """
                {
                  "allowed_domains": ["local", "playground.local"],
                  "allow_subdomains": true,
                  "max_ttl": "72h",
                  "ttl": "24h"
                }
                """);

        String dbConfig =
                """
                {
                  "plugin_name": "postgresql-database-plugin",
                  "allowed_roles": ["playground"],
                  "connection_url": "%s"
                }
                """
                        .formatted(jsonEscape(postgresConnectionUrlFromVaultContainer));
        postExpectSuccess(client, vaultBaseUrl + "/v1/database/config/postgresql", token, dbConfig);

        postExpectSuccess(
                client,
                vaultBaseUrl + "/v1/database/roles/playground",
                token,
                """
                {
                  "db_name": "postgresql",
                  "creation_statements": [
                    "CREATE ROLE \\"{{name}}\\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'",
                    "GRANT CONNECT ON DATABASE postgres TO \\"{{name}}\\""
                  ],
                  "revocation_statements": [
                    "DROP ROLE IF EXISTS \\"{{name}}\\""
                  ],
                  "default_ttl": "1h",
                  "max_ttl": "24h"
                }
                """);
    }

    private static String normalizeBase(String vaultBaseUrl) {
        return vaultBaseUrl.endsWith("/") ? vaultBaseUrl.substring(0, vaultBaseUrl.length() - 1) : vaultBaseUrl;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void awaitVault(HttpClient client, String base, String token) {
        String health = base + "/v1/sys/health";
        for (int i = 0; i < 90; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(health))
                        .timeout(Duration.ofSeconds(2))
                        .header("X-Vault-Token", token)
                        .GET()
                        .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() == 200 || res.statusCode() == 429 || res.statusCode() == 472 || res.statusCode() == 473) {
                    return;
                }
            } catch (Exception ignored) {
                // retry
            }
            sleep();
        }
        throw new IllegalStateException("Vault did not become ready at " + base);
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void postIgnoreExists(HttpClient client, String url, String token, String json) {
        HttpResponse<String> res = sendPost(client, url, token, json);
        if (res.statusCode() == 200 || res.statusCode() == 204) {
            return;
        }
        if (res.statusCode() == 400 && res.body() != null && res.body().contains("path is already in use")) {
            return;
        }
        throw new IllegalStateException("Vault POST failed " + res.statusCode() + " " + url + " body=" + res.body());
    }

    private static void postExpectSuccess(HttpClient client, String url, String token, String json) {
        HttpResponse<String> res = sendPost(client, url, token, json);
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("Vault POST failed " + res.statusCode() + " " + url + " body=" + res.body());
        }
    }

    private static HttpResponse<String> sendPost(HttpClient client, String url, String token, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
