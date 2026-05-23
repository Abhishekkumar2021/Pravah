package io.pravah.runnerservice;

import io.pravah.test.containers.PostgresContainerExtension;
import io.pravah.test.security.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(PostgresContainerExtension.class)
@Import(TestSecurityConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=localhost:29092",
      "grpc.server.port=0",
      "pravah.security.jwt.jwks-url=http://127.0.0.1:9/jwks",
      "pravah.internal-service.secret=test-secret",
      "pravah.execution-service.base-url=http://127.0.0.1:8084"
    })
class RunnerServiceStartupIT {

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerExtension::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerExtension::getUsername);
    registry.add("spring.datasource.password", PostgresContainerExtension::getPassword);
  }

  @Test
  void contextLoads() {}
}
