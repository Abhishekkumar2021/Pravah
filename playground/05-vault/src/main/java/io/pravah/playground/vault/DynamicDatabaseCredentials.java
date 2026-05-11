package io.pravah.playground.vault;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Reads short-lived PostgreSQL users from the Database secrets engine (ADR-007).
 *
 * <p>Each {@link VaultTemplate#read(String)} call to {@code database/creds/&lt;role&gt;} returns a new
 * lease with unique username/password until TTL expires — never a long-lived static DB password in config.
 */
@Service
public class DynamicDatabaseCredentials {

    private final VaultTemplate vault;

    public DynamicDatabaseCredentials(VaultTemplate vault) {
        this.vault = vault;
    }

    /** Lease credentials for the configured Vault role (default {@code playground}). */
    public Optional<Lease> leasePostgresUser(String role) {
        VaultResponse response = vault.read("database/creds/" + role);
        if (response == null || response.getData() == null) {
            return Optional.empty();
        }
        Map<String, Object> data = response.getData();
        Object u = data.get("username");
        Object p = data.get("password");
        if (u == null || p == null) {
            return Optional.empty();
        }
        return Optional.of(new Lease(u.toString(), p.toString(), response.getLeaseDuration()));
    }

    public record Lease(String username, String password, long leaseDurationSeconds) {}
}
