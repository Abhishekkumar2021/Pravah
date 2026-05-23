package io.pravah.connect.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pravah.test.containers.PostgresContainerExtension;
import io.pravah.test.security.TestJwtIssuer;
import io.pravah.test.security.TestSecurityConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(PostgresContainerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=localhost:29092",
      "pravah.security.jwt.jwks-url=http://127.0.0.1:9/jwks",
      "pravah.security.jwt.issuer=pravah-dev",
      "pravah.internal-service.secret=test-secret"
    })
class ConnectorApiIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestJwtIssuer testJwtIssuer;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
  }

  @Test
  void listConnectors_returnsCatalogWithJwt() throws Exception {
    String token =
        testJwtIssuer.generateAccessToken(
            UUID.randomUUID(), UUID.randomUUID(), List.of("editor"), List.of("pipelines:read"));

    mockMvc
        .perform(get("/api/v1/connectors").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void listConnectors_requiresAuth() throws Exception {
    mockMvc.perform(get("/api/v1/connectors")).andExpect(status().isUnauthorized());
  }
}
