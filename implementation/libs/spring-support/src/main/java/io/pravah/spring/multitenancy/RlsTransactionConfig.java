package io.pravah.spring.multitenancy;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Ensures declarative transactions start before {@link RlsAspect} runs.
 *
 * <p>{@code SET LOCAL} requires an active transaction. Spring's transaction advisor defaults to
 * {@link Ordered#LOWEST_PRECEDENCE}; we set it to {@link Ordered#HIGHEST_PRECEDENCE} so the
 * transaction begins before {@link RlsAspect} (which runs at {@code LOWEST_PRECEDENCE}) executes
 * its {@code SET LOCAL} statement.
 *
 * @see <a href="docs/adr/ADR-013-postgresql-rls-tenant-isolation.md">ADR-013</a>
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class RlsTransactionConfig {}
