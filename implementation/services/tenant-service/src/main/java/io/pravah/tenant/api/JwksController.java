package io.pravah.tenant.api;

import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the JSON Web Key Set (JWKS) endpoint for JWT verification.
 *
 * <p>Per ADR-009, other services fetch public keys from this endpoint to verify JWT signatures.
 * This endpoint is publicly accessible (no authentication required).
 *
 * <p>Services should cache the JWKS for 5 minutes and refresh periodically.
 */
@RestController
public class JwksController {

  private final JwtTokenIssuer jwtTokenIssuer;

  public JwksController(JwtTokenIssuer jwtTokenIssuer) {
    this.jwtTokenIssuer = jwtTokenIssuer;
  }

  /**
   * Returns the JWKS containing public keys for JWT verification.
   *
   * @return the JWKS JSON
   */
  @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public String getJwks() {
    return jwtTokenIssuer.getJwksJson();
  }
}
