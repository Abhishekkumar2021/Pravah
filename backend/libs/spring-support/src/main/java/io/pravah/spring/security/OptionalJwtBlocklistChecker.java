package io.pravah.spring.security;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional wrapper so {@link ApiTenantJwtFilter} works when Redis is not configured. */
@Component
public class OptionalJwtBlocklistChecker {

  private final Optional<JwtBlocklistChecker> delegate;
  private final boolean blocklistRequired;

  public OptionalJwtBlocklistChecker(
      @Autowired(required = false) JwtBlocklistChecker delegate,
      @Value("${pravah.security.jwt.blocklist-required:false}") boolean blocklistRequired) {
    this.delegate = Optional.ofNullable(delegate);
    this.blocklistRequired = blocklistRequired;
  }

  /**
   * Returns true when the token is revoked or when blocklist enforcement cannot be performed safely
   * (fail closed per ADR-012).
   */
  public boolean isBlocklisted(String jti) {
    if (delegate.isPresent()) {
      return delegate.get().isBlocklisted(jti);
    }
    if (blocklistRequired) {
      return true;
    }
    return false;
  }
}
