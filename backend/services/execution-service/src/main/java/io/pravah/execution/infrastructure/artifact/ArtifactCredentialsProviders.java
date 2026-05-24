package io.pravah.execution.infrastructure.artifact;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/** Selects S3 credentials for MinIO (static keys) vs AWS IRSA (default chain). */
final class ArtifactCredentialsProviders {

  private ArtifactCredentialsProviders() {}

  static AwsCredentialsProvider forArtifactStorage(ArtifactStorageProperties props) {
    if (props.useDefaultCredentials()) {
      return DefaultCredentialsProvider.create();
    }
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
  }

  static boolean usePathStyleAccess(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return true;
    }
    return !endpoint.contains("amazonaws.com");
  }
}
