package io.pravah.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Connect Service Application Entry Point.
 * <p>
 * Manages external integrations with source control (GitHub, GitLab, Bitbucket)
 * and other third-party services.
 *
 * @see <a href="../../../docs/lld/02-database-erd.md">Database ERD - Connect Domain</a>
 */
@SpringBootApplication
public class ConnectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectServiceApplication.class, args);
    }
}
