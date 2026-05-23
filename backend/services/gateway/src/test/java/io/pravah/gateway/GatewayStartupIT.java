package io.pravah.gateway;

import io.pravah.test.containers.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

@ExtendWith(RedisContainerExtension.class)
@Import(GatewayStartupIT.TestJwtDecoderConfig.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.main.web-application-type=reactive"})
@TestPropertySource(
    properties = {
      "pravah.ratelimit.enabled=false",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:9/jwks"
    })
class GatewayStartupIT {

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", RedisContainerExtension::getHost);
    registry.add("spring.data.redis.port", RedisContainerExtension::getPort);
  }

  @Test
  void contextLoads() {}

  @TestConfiguration
  static class TestJwtDecoderConfig {

    @Bean
    @Primary
    ReactiveJwtDecoder reactiveJwtDecoder() {
      return token -> Mono.error(new UnsupportedOperationException("test decoder"));
    }
  }
}
