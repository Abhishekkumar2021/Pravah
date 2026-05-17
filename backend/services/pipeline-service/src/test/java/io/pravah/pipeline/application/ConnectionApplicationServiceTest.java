package io.pravah.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.pipeline.api.dto.CreateConnectionRequest;
import io.pravah.pipeline.infrastructure.persistence.entity.ConnectionEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.ConnectionRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionApplicationServiceTest {

  @Mock private ConnectionRepository connectionRepository;

  private ConnectionCredentialResolver credentialResolver;
  private ConnectionApplicationService service;
  private UUID tenantId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    credentialResolver = new ConnectionCredentialResolver();
    service = new ConnectionApplicationService(connectionRepository, credentialResolver);
    tenantId = UUID.randomUUID();
    userId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createConnection_postgres_persistsEntity() {
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.createConnection(
            new CreateConnectionRequest(
                "warehouse",
                "postgres",
                Map.of(
                    "host", "localhost",
                    "port", 5432,
                    "database", "pravah",
                    "username", "pravah",
                    "credentials", Map.of("password", "env:PRAVAH_DB_PASSWORD"))));

    assertThat(response.name()).isEqualTo("warehouse");
    assertThat(response.type()).isEqualTo("postgres");
    assertThat(response.config()).containsEntry("host", "localhost");

    ArgumentCaptor<ConnectionEntity> captor = ArgumentCaptor.forClass(ConnectionEntity.class);
    verify(connectionRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void resolveForExecution_parsesConfig() {
    Map<String, Object> config =
        Map.of(
            "url", "jdbc:postgresql://localhost:5432/pravah",
            "username", "pravah",
            "credentials", Map.of("password", "env:TEST_UNUSED_VAR"));
    ConnectionEntity entity =
        new ConnectionEntity(tenantId, "warehouse", "postgres", config, userId, Instant.now());
    when(connectionRepository.findByTenantIdAndName(tenantId, "warehouse"))
        .thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.resolveForExecution("warehouse"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TEST_UNUSED_VAR")
        .hasMessageContaining("not set");
  }

  @Test
  void validateConnectionReferences_unknownName_throws() {
    when(connectionRepository.existsByTenantIdAndName(tenantId, "missing")).thenReturn(false);

    assertThatThrownBy(() -> service.validateConnectionReferences(java.util.List.of("missing")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown connection");
  }

  @Test
  void getConnection_unknownId_throwsNotFound() {
    UUID id = UUID.randomUUID();
    when(connectionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getConnection(id)).isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void createConnection_missingCredentials_throws() {
    assertThatThrownBy(
            () ->
                service.createConnection(
                    new CreateConnectionRequest(
                        "warehouse",
                        "postgres",
                        Map.of(
                            "host", "localhost",
                            "port", 5432,
                            "database", "pravah",
                            "username", "pravah"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("credentials.password");
  }

  @Test
  void createConnection_invalidCredentialReference_throws() {
    assertThatThrownBy(
            () ->
                service.createConnection(
                    new CreateConnectionRequest(
                        "warehouse",
                        "postgres",
                        Map.of(
                            "host", "localhost",
                            "port", 5432,
                            "database", "pravah",
                            "username", "pravah",
                            "credentials", Map.of("password", "invalid-format")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported credential reference");
  }
}
