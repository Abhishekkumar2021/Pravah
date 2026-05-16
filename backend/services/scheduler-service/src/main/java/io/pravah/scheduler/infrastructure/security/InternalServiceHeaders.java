package io.pravah.scheduler.infrastructure.security;

import java.util.UUID;
import org.springframework.http.HttpHeaders;

public final class InternalServiceHeaders {

  public static final String SECRET_HEADER = "X-Pravah-Internal-Secret";
  public static final String TENANT_HEADER = "X-Pravah-Tenant-Id";

  private InternalServiceHeaders() {}

  public static void apply(HttpHeaders headers, String secret, UUID tenantId) {
    headers.set(SECRET_HEADER, secret);
    headers.set(TENANT_HEADER, tenantId.toString());
  }
}
