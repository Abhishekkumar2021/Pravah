package io.pravah.runnerservice.infrastructure.grpc;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sets {@link TenantContext} for runner gRPC calls from metadata. Registration requires a bootstrap
 * secret aligned with {@code pravah.runner.bootstrap-secret} (local default: internal service
 * secret).
 */
@Component
public class RunnerGrpcContextInterceptor implements ServerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(RunnerGrpcContextInterceptor.class);

  private final String bootstrapSecret;

  public RunnerGrpcContextInterceptor(
      @Value("${pravah.runner.bootstrap-secret:}") String bootstrapSecret,
      @Value("${pravah.internal-service.secret:}") String internalSecret) {
    String configured =
        bootstrapSecret != null && !bootstrapSecret.isBlank() ? bootstrapSecret : internalSecret;
    this.bootstrapSecret = configured != null ? configured : "";
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String method = call.getMethodDescriptor().getFullMethodName();
    boolean isRegister = method != null && method.endsWith("/RegisterRunner");
    boolean isConnect = method != null && method.endsWith("/Connect");
    boolean isAdminQuery =
        method != null && (method.endsWith("/GetRunner") || method.endsWith("/ListRunners"));

    UUID tenantId = parseTenantId(headers);

    // RegisterRunner and admin queries require tenant-id and bootstrap-secret upfront
    if (isRegister || isAdminQuery) {
      if (tenantId == null) {
        call.close(
            Status.UNAUTHENTICATED.withDescription(
                "Missing or invalid x-pravah-tenant-id metadata"),
            new Metadata());
        return new ServerCall.Listener<>() {};
      }
      if (!validateBootstrapSecret(headers)) {
        call.close(
            Status.PERMISSION_DENIED.withDescription("Invalid runner bootstrap secret"),
            new Metadata());
        return new ServerCall.Listener<>() {};
      }
    }

    // Connect stream validates auth on first heartbeat (token-based), not here.
    // We still extract tenant if present for logging/context, but don't reject.
    if (isConnect) {
      log.debug(
          "Connect stream opened; tenantId={} (auth will happen on first heartbeat)",
          tenantId != null ? tenantId : "not-provided");
    }

    ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
    if (tenantId == null) {
      return delegate;
    }
    return new TenantContextListener<>(delegate, tenantId);
  }

  private UUID parseTenantId(Metadata headers) {
    String raw =
        headers.get(
            Metadata.Key.of(RunnerGrpcAuth.TENANT_METADATA_KEY, Metadata.ASCII_STRING_MARSHALLER));
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid tenant id in gRPC metadata: {}", raw);
      return null;
    }
  }

  private boolean validateBootstrapSecret(Metadata headers) {
    if (bootstrapSecret.isBlank()) {
      log.warn("Runner bootstrap secret is not configured");
      return false;
    }
    String provided =
        headers.get(
            Metadata.Key.of(
                RunnerGrpcAuth.BOOTSTRAP_SECRET_METADATA_KEY, Metadata.ASCII_STRING_MARSHALLER));
    if (provided == null) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        bootstrapSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static final class TenantContextListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

    private final UUID tenantId;

    TenantContextListener(ServerCall.Listener<ReqT> delegate, UUID tenantId) {
      super(delegate);
      this.tenantId = tenantId;
    }

    @Override
    public void onMessage(ReqT message) {
      TenantContext.setCurrentTenantId(tenantId);
      try {
        super.onMessage(message);
      } finally {
        TenantContext.clear();
      }
    }
  }
}
