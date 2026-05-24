package io.pravah.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Ensures gateway routes tenant secrets API to pipeline-service (pathway #8). */
class GatewayPipelineRoutesTest {

  @Test
  @SuppressWarnings("unchecked")
  void pipelineServiceRoute_includesSecretsPath() {
    Yaml yaml = new Yaml();
    InputStream input = getClass().getClassLoader().getResourceAsStream("application.yml");
    assertThat(input).isNotNull();
    Map<String, Object> root = yaml.load(input);
    Map<String, Object> spring = (Map<String, Object>) root.get("spring");
    Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
    Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
    List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");

    Map<String, Object> pipelineRoute =
        routes.stream().filter(r -> "pipeline-service".equals(r.get("id"))).findFirst().orElseThrow();

    List<String> predicates = (List<String>) pipelineRoute.get("predicates");
    String pathPredicate =
        predicates.stream().filter(p -> p.startsWith("Path=")).findFirst().orElse("");

    assertThat(pathPredicate).contains("/api/v1/secrets/**");
    assertThat(pathPredicate).contains("/api/v1/connections/**");
  }
}
