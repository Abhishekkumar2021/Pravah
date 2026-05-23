package io.pravah.spring.security;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Optional wrapper so {@link ApiTenantJwtFilter} works when Redis is not configured. */
@Component
public class OptionalJwtBlocklistChecker {

  private final Optional<JwtBlocklistChecker> delegate;

  public OptionalJwtBlocklistChecker(@Autowired(required = false) JwtBlocklistChecker delegate) {
    this.delegate = Optional.ofNullable(delegate);
  }

  public boolean isBlocklisted(String jti) {
    return delegate.map(c -> c.isBlocklisted(jti)).orElse(false);
  }
}
