package io.pravah.common.grpc;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

/**
 * Parses runner workload identity from X.509 client certificates.
 *
 * <p>Preferred: SPIFFE URI SAN {@code
 * spiffe://<trust-domain>/tenant/<tenant-uuid>/runner/<runner-uuid>} or {@code
 * spiffe://<trust-domain>/runner/<runner-uuid>} (tenant resolved from database).
 */
public final class RunnerCertificateIdentityParser {

  private static final int SAN_URI = 6;

  private static final Pattern SPIFFE_TENANT_RUNNER =
      Pattern.compile("^spiffe://[^/]+/tenant/([0-9a-fA-F-]{36})/runner/([0-9a-fA-F-]{36})$");

  private static final Pattern SPIFFE_RUNNER_ONLY =
      Pattern.compile("^spiffe://[^/]+/runner/([0-9a-fA-F-]{36})$");

  private RunnerCertificateIdentityParser() {}

  public static Optional<RunnerCertificateIdentity> parse(X509Certificate certificate) {
    if (certificate == null) {
      return Optional.empty();
    }
    Optional<RunnerCertificateIdentity> fromSan = parseSubjectAltNames(certificate);
    if (fromSan.isPresent()) {
      return fromSan;
    }
    return parseSubjectCn(certificate);
  }

  private static Optional<RunnerCertificateIdentity> parseSubjectAltNames(
      X509Certificate certificate) {
    try {
      Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
      if (sans == null) {
        return Optional.empty();
      }
      for (List<?> entry : sans) {
        if (entry.size() < 2) {
          continue;
        }
        Object type = entry.get(0);
        if (!(type instanceof Integer sanType) || sanType != SAN_URI) {
          continue;
        }
        Object value = entry.get(1);
        if (!(value instanceof String uri)) {
          continue;
        }
        Optional<RunnerCertificateIdentity> parsed = parseSpiffeUri(uri);
        if (parsed.isPresent()) {
          return parsed;
        }
      }
    } catch (Exception ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  static Optional<RunnerCertificateIdentity> parseSpiffeUri(String uri) {
    Matcher tenantRunner = SPIFFE_TENANT_RUNNER.matcher(uri);
    if (tenantRunner.matches()) {
      return Optional.of(
          new RunnerCertificateIdentity(
              UUID.fromString(tenantRunner.group(2)),
              Optional.of(UUID.fromString(tenantRunner.group(1)))));
    }
    Matcher runnerOnly = SPIFFE_RUNNER_ONLY.matcher(uri);
    if (runnerOnly.matches()) {
      return Optional.of(
          new RunnerCertificateIdentity(UUID.fromString(runnerOnly.group(1)), Optional.empty()));
    }
    return Optional.empty();
  }

  /** Builds the SPIFFE URI for a runner workload certificate (ADR-008). */
  public static String runnerSpiffeUri(String trustDomain, UUID tenantId, UUID runnerId) {
    Objects.requireNonNull(trustDomain, "trustDomain");
    Objects.requireNonNull(runnerId, "runnerId");
    if (tenantId != null) {
      return "spiffe://" + trustDomain + "/tenant/" + tenantId + "/runner/" + runnerId;
    }
    return "spiffe://" + trustDomain + "/runner/" + runnerId;
  }

  private static Optional<RunnerCertificateIdentity> parseSubjectCn(X509Certificate certificate) {
    try {
      X500Principal principal = certificate.getSubjectX500Principal();
      LdapName ldapName = new LdapName(principal.getName());
      for (Rdn rdn : ldapName.getRdns()) {
        if (!"CN".equalsIgnoreCase(rdn.getType())) {
          continue;
        }
        String cn = String.valueOf(rdn.getValue());
        try {
          return Optional.of(new RunnerCertificateIdentity(UUID.fromString(cn), Optional.empty()));
        } catch (IllegalArgumentException ignored) {
          return Optional.empty();
        }
      }
    } catch (Exception ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }
}
