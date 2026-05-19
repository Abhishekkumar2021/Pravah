package io.pravah.pipeline.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration for Pipeline Service (US-11.12).
 *
 * <p>Provides Swagger UI at /swagger-ui.html and OpenAPI spec at /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

  @Value("${spring.application.name:pipeline-service}")
  private String applicationName;

  @Bean
  public OpenAPI pipelineServiceOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Pravah Pipeline Service API")
                .description(
                    """
                    REST API for managing pipeline definitions, versions, and YAML validation.

                    ## Authentication
                    All endpoints require JWT Bearer token authentication. Obtain tokens via
                    the tenant-service `/api/v1/auth/login` endpoint.

                    ## Resources
                    - **Pipelines**: CRUD operations for workflow definitions
                    - **Connections**: Manage database and service connections
                    - **Secrets**: Manage encrypted secrets (references only)
                    """)
                .version("1.0.0")
                .contact(new Contact().name("Pravah Team").email("team@pravah.io"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(
            List.of(
                new Server().url("http://localhost:8083").description("Direct service access"),
                new Server().url("http://localhost:8080").description("Via API Gateway")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token from /api/v1/auth/login")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
