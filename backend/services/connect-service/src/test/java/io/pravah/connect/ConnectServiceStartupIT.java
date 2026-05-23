package io.pravah.connect;

import io.pravah.test.containers.PostgresContainerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(PostgresContainerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=localhost:29092",
      "pravah.security.jwt.jwks-url=http://127.0.0.1:9/jwks",
      "pravah.security.jwt.issuer=pravah-dev",
      "pravah.internal-service.secret=test-secret"
    })
class ConnectServiceStartupIT {

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
  }

  @Test
  void contextLoads() {}
}
