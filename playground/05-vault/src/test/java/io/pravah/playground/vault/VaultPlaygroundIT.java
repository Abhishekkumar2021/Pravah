package io.pravah.playground.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class VaultPlaygroundIT {

    private static final Network NETWORK = Network.newNetwork();

    private static final String ROOT_TOKEN = "root";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withNetwork(NETWORK)
            .withNetworkAliases("pg");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> VAULT =
            new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.15"))
                    .withExposedPorts(8200)
                    .withNetwork(NETWORK)
                    .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
                    .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
                    .withEnv("VAULT_ADDR", "http://127.0.0.1:8200")
                    .withCommand("server", "-dev");

    @BeforeAll
    static void initEngines() {
        String vaultUrl = "http://127.0.0.1:" + VAULT.getMappedPort(8200);
        String pgFromVault = "postgresql://postgres:postgres@pg:5432/postgres?sslmode=disable";
        PlaygroundVaultHttpBootstrap.bootstrap(vaultUrl, ROOT_TOKEN, pgFromVault);
    }

    @DynamicPropertySource
    static void vaultProps(DynamicPropertyRegistry registry) {
        registry.add("playground.vault.uri", () -> "http://127.0.0.1:" + VAULT.getMappedPort(8200));
        registry.add("playground.vault.token", () -> ROOT_TOKEN);
    }

    @Autowired
    private DynamicDatabaseCredentials databaseCredentials;

    @Autowired
    private PkiIssueService pkiIssueService;

    @Test
    void leasedDatabaseUser_canConnectToPostgres() throws Exception {
        DynamicDatabaseCredentials.Lease lease =
                databaseCredentials.leasePostgresUser("playground").orElseThrow();

        String url = POSTGRES.getJdbcUrl();
        try (Connection c = DriverManager.getConnection(url, lease.username(), lease.password())) {
            assertThat(c.isValid(5)).isTrue();
        }
    }

    @Test
    void pkiIssuesCertificateBundle() {
        PkiIssueService.IssuedCertificate cert =
                pkiIssueService.issue("svc.playground.local", "1h").orElseThrow();

        assertThat(cert.certificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(cert.privateKeyPem()).matches(s -> s.contains("PRIVATE KEY"));
    }
}
