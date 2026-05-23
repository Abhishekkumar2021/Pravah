package io.pravah.runnerservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.repository.RunnerRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunnerServiceAuthTest {

  @Mock private RunnerRepository repository;
  @Mock private RunnerConnectionManager connectionManager;
  @Mock private io.pravah.spring.multitenancy.SystemMaintenanceRlsHelper maintenanceRlsHelper;

  private RunnerService runnerService;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    runnerService = new RunnerService(repository, connectionManager, maintenanceRlsHelper);
    tenantId = UUID.randomUUID();
  }

  @Test
  void registerRunner_reauthenticatesWithValidToken() {
    Runner existing = new Runner();
    existing.setId(UUID.randomUUID());
    existing.setTenantId(tenantId);
    existing.setName("worker-1");
    existing.setTokenHash(sha256("secret-token"));
    existing.setHeartbeatIntervalSeconds(30);

    when(repository.findByTenantIdAndName(tenantId, "worker-1")).thenReturn(Optional.of(existing));
    when(repository.save(any(Runner.class))).thenAnswer(inv -> inv.getArgument(0));

    var request =
        new RunnerService.RegisterRequest(
            "worker-1",
            "0.2.0",
            Map.of("env", "dev"),
            4,
            List.of("shell"),
            1024L,
            2,
            "secret-token");

    RunnerService.RegisterResult result = runnerService.registerRunner(tenantId, request);

    assertThat(result.runnerId()).isEqualTo(existing.getId());
    assertThat(result.token()).isEqualTo("secret-token");
  }

  @Test
  void validateToken_acceptsMatchingHash() {
    UUID runnerId = UUID.randomUUID();
    Runner runner = new Runner();
    runner.setId(runnerId);
    runner.setTokenHash(sha256("abc"));

    when(repository.findById(runnerId)).thenReturn(Optional.of(runner));

    assertThat(runnerService.validateToken(runnerId, "abc")).isPresent();
    assertThat(runnerService.validateToken(runnerId, "wrong")).isEmpty();
  }

  @Test
  void registerRunner_duplicateNameWithoutToken_throws() {
    Runner existing = new Runner();
    existing.setName("worker-1");
    when(repository.findByTenantIdAndName(tenantId, "worker-1")).thenReturn(Optional.of(existing));

    var request =
        new RunnerService.RegisterRequest(
            "worker-1", "0.1.0", Map.of(), 2, List.of("shell"), 512L, 1, null);

    assertThatThrownBy(() -> runnerService.registerRunner(tenantId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void registerRunner_rejectsInvalidToken() {
    Runner existing = new Runner();
    existing.setId(UUID.randomUUID());
    existing.setName("worker-1");
    existing.setTokenHash(sha256("correct-token"));
    when(repository.findByTenantIdAndName(tenantId, "worker-1")).thenReturn(Optional.of(existing));

    var request =
        new RunnerService.RegisterRequest(
            "worker-1", "0.2.0", Map.of(), 4, List.of("shell"), 1024L, 2, "wrong-token");

    assertThatThrownBy(() -> runnerService.registerRunner(tenantId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid registration token");
  }

  private static String sha256(String token) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
