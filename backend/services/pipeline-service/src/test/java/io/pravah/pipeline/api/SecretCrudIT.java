package io.pravah.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.pipeline.PipelineServiceApplication;
import io.pravah.pipeline.api.dto.CreateSecretRequest;
import io.pravah.pipeline.api.dto.SecretResponse;
import io.pravah.pipeline.api.dto.UpdateSecretRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = PipelineServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecretCrudIT extends AbstractPipelinePostgresIT {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void createListGetUpdateDelete_roundTrip() {
    HttpHeaders headers = createHeaders();

    CreateSecretRequest createRequest =
        new CreateSecretRequest("api_key", "External API key", "env", "MY_API_KEY");

    ResponseEntity<SecretResponse> createResponse =
        restTemplate.postForEntity(
            baseUrl() + "/api/v1/secrets",
            new HttpEntity<>(createRequest, headers),
            SecretResponse.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    SecretResponse created = createResponse.getBody();
    assertThat(created).isNotNull();
    assertThat(created.id()).isNotNull();
    assertThat(created.name()).isEqualTo("api_key");
    assertThat(created.provider()).isEqualTo("env");
    assertThat(created.providerPath()).isEqualTo("MY_API_KEY");

    ResponseEntity<SecretResponse[]> listResponse =
        restTemplate.exchange(
            baseUrl() + "/api/v1/secrets",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SecretResponse[].class);

    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody()).hasSize(1);

    ResponseEntity<SecretResponse> getResponse =
        restTemplate.exchange(
            baseUrl() + "/api/v1/secrets/" + created.id(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SecretResponse.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().name()).isEqualTo("api_key");

    ResponseEntity<SecretResponse> getByNameResponse =
        restTemplate.exchange(
            baseUrl() + "/api/v1/secrets/by-name/api_key",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SecretResponse.class);

    assertThat(getByNameResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getByNameResponse.getBody().id()).isEqualTo(created.id());

    UpdateSecretRequest updateRequest =
        new UpdateSecretRequest("Updated description", "env", "NEW_ENV_VAR");

    ResponseEntity<SecretResponse> updateResponse =
        restTemplate.exchange(
            baseUrl() + "/api/v1/secrets/" + created.id(),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, headers),
            SecretResponse.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResponse.getBody().description()).isEqualTo("Updated description");
    assertThat(updateResponse.getBody().providerPath()).isEqualTo("NEW_ENV_VAR");

    ResponseEntity<Void> deleteResponse =
        restTemplate.exchange(
            baseUrl() + "/api/v1/secrets/" + created.id(),
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<SecretResponse[]> afterDelete =
        restTemplate.exchange(
            baseUrl() + "/api/v1/secrets",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            SecretResponse[].class);

    assertThat(afterDelete.getBody()).isEmpty();
  }

  @Test
  void createSecret_duplicateName_returns409() {
    HttpHeaders headers = createHeaders();
    CreateSecretRequest request = new CreateSecretRequest("dup_secret", null, "env", "VAR");

    restTemplate.postForEntity(
        baseUrl() + "/api/v1/secrets", new HttpEntity<>(request, headers), SecretResponse.class);

    ResponseEntity<String> duplicateResponse =
        restTemplate.postForEntity(
            baseUrl() + "/api/v1/secrets", new HttpEntity<>(request, headers), String.class);

    assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void createSecret_invalidName_returns400() {
    HttpHeaders headers = createHeaders();
    CreateSecretRequest request = new CreateSecretRequest("Invalid-Name", null, "env", "VAR");

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            baseUrl() + "/api/v1/secrets", new HttpEntity<>(request, headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createSecret_invalidProvider_returns400() {
    HttpHeaders headers = createHeaders();
    CreateSecretRequest request =
        new CreateSecretRequest("valid_name", null, "unknown_provider", "path");

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            baseUrl() + "/api/v1/secrets", new HttpEntity<>(request, headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createSecret_vaultProviderWithoutKey_returns400() {
    HttpHeaders headers = createHeaders();
    CreateSecretRequest request =
        new CreateSecretRequest("vault_secret", null, "vault", "secret/path/without/key");

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            baseUrl() + "/api/v1/secrets", new HttpEntity<>(request, headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("key");
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private HttpHeaders createHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("X-Tenant-ID", TENANT_ID.toString());
    headers.set("X-User-ID", USER_ID.toString());
    return headers;
  }
}
