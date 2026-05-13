package io.pravah.test.fixtures;

import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.TenantId;
import net.datafaker.Faker;

import java.util.UUID;

/**
 * Test fixtures for pipeline-related test data.
 */
public final class PipelineFixtures {

    private static final Faker FAKER = new Faker();

    private PipelineFixtures() {
        // Utility class
    }

    /**
     * Generate a random pipeline ID.
     */
    public static PipelineId randomPipelineId() {
        return PipelineId.generate();
    }

    /**
     * Generate a well-known pipeline ID for consistent testing.
     */
    public static PipelineId testPipelineId() {
        return PipelineId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    /**
     * Generate a random pipeline name.
     */
    public static String randomPipelineName() {
        return FAKER.hacker().verb() + "-" + FAKER.hacker().noun();
    }

    /**
     * Generate test pipeline YAML definition.
     */
    public static String samplePipelineYaml(String name, TenantId tenantId) {
        return """
            name: %s
            description: Test pipeline for %s
            
            jobs:
              build:
                runs-on: docker
                image: maven:3.9-eclipse-temurin-21
                steps:
                  - name: Checkout
                    run: echo "Checking out code"
                  - name: Build
                    run: mvn clean package
            
              test:
                needs: [build]
                runs-on: docker
                image: maven:3.9-eclipse-temurin-21
                steps:
                  - name: Run tests
                    run: mvn test
            """.formatted(name, tenantId.value());
    }
}
