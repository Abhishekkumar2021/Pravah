package io.pravah.execution.infrastructure.artifact;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configuration for S3/MinIO artifact storage client.
 *
 * @see docs/adr/ADR-021-minio-artifact-storage.md
 */
@Configuration
@EnableConfigurationProperties(ArtifactStorageProperties.class)
@ConditionalOnProperty(
    prefix = "pravah.artifact",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ArtifactStorageConfig {

  private static final Logger log = LoggerFactory.getLogger(ArtifactStorageConfig.class);

  @Bean
  public S3Client s3Client(ArtifactStorageProperties props) {
    log.info(
        "Initializing S3 client for artifact storage: endpoint={}, bucket={}",
        props.endpoint(),
        props.bucket());

    var credentials = AwsBasicCredentials.create(props.accessKey(), props.secretKey());

    return S3Client.builder()
        .endpointOverride(URI.create(props.endpoint()))
        .region(Region.of(props.region()))
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(ArtifactStorageProperties props) {
    var credentials = AwsBasicCredentials.create(props.accessKey(), props.secretKey());

    return S3Presigner.builder()
        .endpointOverride(URI.create(props.endpoint()))
        .region(Region.of(props.region()))
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Bean
  public ArtifactStorageService artifactStorageService(
      S3Client s3Client, S3Presigner s3Presigner, ArtifactStorageProperties props) {
    return new ArtifactStorageService(s3Client, s3Presigner, props);
  }
}
