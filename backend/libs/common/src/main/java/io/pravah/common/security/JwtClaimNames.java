package io.pravah.common.security;

/**
 * Constants for JWT claim names used across Pravah services.
 *
 * <p>Per ADR-009, these are the standard claim names embedded in access tokens.
 */
public final class JwtClaimNames {

  private JwtClaimNames() {}

  /** The tenant ID claim. Value is a UUID string. */
  public static final String TENANT_ID = "tenant_id";

  /** Standard JWT subject claim. In Pravah, this contains the user ID as a UUID string. */
  public static final String SUBJECT = "sub";

  /** Standard JWT ID claim. Unique identifier for each token, used for revocation. */
  public static final String JWT_ID = "jti";

  /** Standard JWT issuer claim. Identifies the service that issued the token. */
  public static final String ISSUER = "iss";

  /** Standard JWT issued-at claim. Unix timestamp when token was issued. */
  public static final String ISSUED_AT = "iat";

  /** Standard JWT expiration claim. Unix timestamp when token expires. */
  public static final String EXPIRATION = "exp";

  /** Tenant-scoped role names, e.g. {@code ["editor"]}. Per ADR-009. */
  public static final String ROLES = "roles";

  /** Resolved permission strings from the user's role, e.g. {@code ["pipelines:read"]}. */
  public static final String PERMISSIONS = "permissions";

  /** Token type claim: {@code access} or {@code refresh}. */
  public static final String TOKEN_TYPE = "token_type";
}
