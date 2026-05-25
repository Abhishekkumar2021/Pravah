package io.pravah.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tenant Service Application Entry Point.
 *
 * <p>Manages multi-tenancy, user accounts, and role-based access control. This is the source of
 * truth for tenant and user data.
 *
 * @see <a href="../../../docs/lld/02-database-erd.md">Database ERD - Tenant Domain</a>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = {
      "io.pravah.tenant",
      "io.pravah.spring.multitenancy",
      "io.pravah.spring.security"
    })
public class TenantServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TenantServiceApplication.class, args);
  }
}
