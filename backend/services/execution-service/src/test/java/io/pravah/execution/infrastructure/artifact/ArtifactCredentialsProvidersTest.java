package io.pravah.execution.infrastructure.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class ArtifactCredentialsProvidersTest {

  @Test
  void usePathStyleAccess_trueForMinioEndpoint() {
    assertThat(ArtifactCredentialsProviders.usePathStyleAccess("http://localhost:9000")).isTrue();
  }

  @Test
  void usePathStyleAccess_falseForAwsS3Endpoint() {
    assertThat(
            ArtifactCredentialsProviders.usePathStyleAccess("https://s3.us-east-1.amazonaws.com"))
        .isFalse();
  }

  @Test
  void forArtifactStorage_usesDefaultChainWhenIrsaEnabled() {
    var props =
        new ArtifactStorageProperties(
            true,
            "https://s3.us-east-1.amazonaws.com",
            "us-east-1",
            "",
            "",
            "pravah-artifacts",
            java.time.Duration.ofMinutes(15),
            1024,
            30,
            true);

    assertThat(ArtifactCredentialsProviders.forArtifactStorage(props))
        .isInstanceOf(DefaultCredentialsProvider.class);
  }

  @Test
  void forArtifactStorage_usesStaticCredentialsWhenKeysPresent() {
    var props =
        new ArtifactStorageProperties(
            true,
            "http://localhost:9000",
            "us-east-1",
            "access",
            "secret",
            "pravah-artifacts",
            java.time.Duration.ofMinutes(15),
            1024,
            30,
            false);

    assertThat(ArtifactCredentialsProviders.forArtifactStorage(props))
        .isInstanceOf(StaticCredentialsProvider.class);
  }
}
