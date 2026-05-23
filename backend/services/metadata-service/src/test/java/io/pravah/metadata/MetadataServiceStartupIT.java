package io.pravah.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "pravah.security.jwt.jwks-url=http://127.0.0.1:9/jwks",
      "pravah.security.jwt.issuer=pravah-dev"
    })
class MetadataServiceStartupIT {

  @Test
  void contextLoads() {}
}
