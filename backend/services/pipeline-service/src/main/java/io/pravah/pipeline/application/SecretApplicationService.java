package io.pravah.pipeline.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.UserId;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.pipeline.api.dto.CreateSecretRequest;
import io.pravah.pipeline.api.dto.SecretResponse;
import io.pravah.pipeline.api.dto.UpdateSecretRequest;
import io.pravah.pipeline.domain.SecretProvider;
import io.pravah.pipeline.infrastructure.persistence.entity.TenantSecretEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.TenantSecretRepository;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for tenant secret management.
 *
 * <p>Manages secret references (name → provider + path) but never handles actual secret values.
 */
@Service
public class SecretApplicationService {

  private static final Logger log = LoggerFactory.getLogger(SecretApplicationService.class);

  private final TenantSecretRepository secretRepository;
  private final EntityManager entityManager;

  public SecretApplicationService(
      TenantSecretRepository secretRepository, EntityManager entityManager) {
    this.secretRepository = secretRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public SecretResponse createSecret(CreateSecretRequest request) {
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();
    String provider = SecretProvider.parse(request.provider()).toValue();
    validateProviderPath(provider, request.providerPath());

    log.info(
        "Creating secret reference",
        kv("tenant_id", tenantId),
        kv("secret_name", request.name()),
        kv("provider", provider));

    try {
      TenantSecretEntity entity =
          secretRepository.save(
              new TenantSecretEntity(
                  tenantId,
                  request.name().trim(),
                  request.description(),
                  provider,
                  request.providerPath().trim(),
                  userId.value(),
                  Instant.now()));
      entityManager.flush();
      return toResponse(entity);
    } catch (DataIntegrityViolationException e) {
      throw duplicateSecretName(request.name(), e);
    } catch (JpaSystemException e) {
      if (isUniqueConstraintViolation(e)) {
        throw duplicateSecretName(request.name(), e);
      }
      throw e;
    } catch (PersistenceException e) {
      if (isUniqueConstraintViolation(e)) {
        throw duplicateSecretName(request.name(), e);
      }
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public List<SecretResponse> listSecrets() {
    return secretRepository.findByTenantIdOrderByNameAsc(requireTenantId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public SecretResponse getSecret(UUID secretId) {
    return toResponse(findSecretOrThrow(secretId));
  }

  @Transactional(readOnly = true)
  public SecretResponse getSecretByName(String name) {
    return toResponse(findByNameOrThrow(name));
  }

  @Transactional
  public SecretResponse updateSecret(UUID secretId, UpdateSecretRequest request) {
    UUID tenantId = requireTenantId();
    requireUserId();

    TenantSecretEntity entity = findSecretOrThrow(secretId);
    String provider =
        request.provider() != null
            ? SecretProvider.parse(request.provider()).toValue()
            : entity.getProvider();
    String providerPath =
        request.providerPath() != null ? request.providerPath().trim() : entity.getProviderPath();

    if (request.provider() != null || request.providerPath() != null) {
      validateProviderPath(provider, providerPath);
    }

    log.info(
        "Updating secret reference",
        kv("tenant_id", tenantId),
        kv("secret_id", secretId),
        kv("secret_name", entity.getName()),
        kv("provider", provider));

    entity.update(
        request.description() != null ? request.description() : entity.getDescription(),
        provider,
        providerPath);
    return toResponse(entity);
  }

  @Transactional
  public void deleteSecret(UUID secretId) {
    requireTenantId();
    TenantSecretEntity entity = findSecretOrThrow(secretId);
    secretRepository.delete(entity);
    log.info("Deleted secret reference", kv("secret_id", secretId), kv("name", entity.getName()));
  }

  /** Verifies all secret references exist for the current tenant (used during pipeline publish). */
  @Transactional(readOnly = true)
  public void validateSecretReferences(Iterable<String> secretNames) {
    UUID tenantId = requireTenantId();
    for (String name : secretNames) {
      if (name == null || name.isBlank()) {
        continue;
      }
      if (!secretRepository.existsByTenantIdAndName(tenantId, name)) {
        throw new IllegalArgumentException("Unknown secret: '%s'".formatted(name));
      }
    }
  }

  /** Retrieves secret metadata for resolution (internal use). */
  @Transactional(readOnly = true)
  public TenantSecretEntity getSecretEntityByName(UUID tenantId, String name) {
    return secretRepository
        .findByTenantIdAndName(tenantId, name)
        .orElseThrow(() -> new EntityNotFoundException("Secret", name));
  }

  private void validateProviderPath(String provider, String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Provider path is required");
    }
    switch (provider) {
      case "env" -> {
        if (!path.matches("[A-Za-z_][A-Za-z0-9_]*")) {
          throw new IllegalArgumentException(
              "ENV provider path must be a valid environment variable name: " + path);
        }
      }
      case "vault" -> {
        if (!path.contains("#")) {
          throw new IllegalArgumentException(
              "VAULT provider path must include key: path#key. Got: " + path);
        }
      }
      case "aws_sm" -> {
        if (!path.startsWith("arn:aws:secretsmanager:")) {
          log.debug("AWS Secrets Manager path doesn't look like an ARN: {}", path);
        }
      }
    }
  }

  private TenantSecretEntity findSecretOrThrow(UUID secretId) {
    UUID tenantId = requireTenantId();
    return secretRepository
        .findById(secretId)
        .filter(s -> s.getTenantId().equals(tenantId))
        .orElseThrow(() -> new EntityNotFoundException("Secret", secretId));
  }

  private TenantSecretEntity findByNameOrThrow(String name) {
    UUID tenantId = requireTenantId();
    return secretRepository
        .findByTenantIdAndName(tenantId, name)
        .orElseThrow(() -> new EntityNotFoundException("Secret", name));
  }

  private static UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  private static UserId requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("Missing user context");
    }
    return UserId.of(userId);
  }

  private SecretResponse toResponse(TenantSecretEntity entity) {
    return new SecretResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getProvider(),
        entity.getProviderPath(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private DuplicateSecretNameException duplicateSecretName(String name, Throwable cause) {
    log.warn(
        "Duplicate secret name",
        kv("tenant_id", TenantContext.getCurrentTenantId()),
        kv("secret_name", name));
    return new DuplicateSecretNameException(
        "A secret with this name already exists in the tenant", cause);
  }

  private static boolean isUniqueConstraintViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException) {
        return true;
      }
      if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
