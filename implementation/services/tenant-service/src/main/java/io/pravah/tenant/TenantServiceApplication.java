package io.pravah.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tenant Service Application Entry Point.
 * <p>
 * Manages multi-tenancy, user accounts, and role-based access control.
 * This is the source of truth for tenant and user data.
 *
 * @see <a href="../../../docs/lld/02-database-erd.md">Database ERD - Tenant Domain</a>
 */
@SpringBootApplication
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
