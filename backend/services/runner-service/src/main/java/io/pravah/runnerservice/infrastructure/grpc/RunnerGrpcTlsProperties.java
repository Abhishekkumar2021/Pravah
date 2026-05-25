package io.pravah.runnerservice.infrastructure.grpc;

import io.pravah.common.grpc.GrpcTlsConfig;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pravah.runner.grpc.tls")
public class RunnerGrpcTlsProperties {

  private boolean enabled;
  private String certChain = "";
  private String privateKey = "";
  private String clientCa = "";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCertChain() {
    return certChain;
  }

  public void setCertChain(String certChain) {
    this.certChain = certChain;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }

  public String getClientCa() {
    return clientCa;
  }

  public void setClientCa(String clientCa) {
    this.clientCa = clientCa;
  }

  public GrpcTlsConfig toConfig() {
    if (!enabled) {
      return GrpcTlsConfig.disabled();
    }
    Path cert = pathOrNull(certChain);
    Path key = pathOrNull(privateKey);
    Path ca = pathOrNull(clientCa);
    return GrpcTlsConfig.server(cert, key, ca);
  }

  private static Path pathOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Path.of(value);
  }
}
