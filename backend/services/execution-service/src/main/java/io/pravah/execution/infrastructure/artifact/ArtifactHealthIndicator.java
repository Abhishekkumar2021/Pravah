package io.pravah.execution.infrastructure.artifact;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for artifact storage (MinIO/S3).
 */
@Component("artifactStorage")
@ConditionalOnProperty(prefix = "pravah.artifact", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ArtifactHealthIndicator implements HealthIndicator {

    private final ArtifactStorageService artifactStorageService;
    private final ArtifactStorageProperties props;

    public ArtifactHealthIndicator(
            ArtifactStorageService artifactStorageService,
            ArtifactStorageProperties props) {
        this.artifactStorageService = artifactStorageService;
        this.props = props;
    }

    @Override
    public Health health() {
        try {
            if (artifactStorageService.isHealthy()) {
                return Health.up()
                        .withDetail("endpoint", props.endpoint())
                        .withDetail("bucket", props.bucket())
                        .withDetail("retentionDays", props.retentionDays())
                        .build();
            } else {
                return Health.down()
                        .withDetail("endpoint", props.endpoint())
                        .withDetail("bucket", props.bucket())
                        .withDetail("reason", "Bucket not accessible")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", props.endpoint())
                    .withDetail("bucket", props.bucket())
                    .withException(e)
                    .build();
        }
    }
}
