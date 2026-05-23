package io.pravah.spring.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pravah.vault")
public record VaultProperties(
    boolean enabled, String address, Auth auth, int requestTimeoutSeconds) {

  public VaultProperties {
    if (address == null || address.isBlank()) {
      address = "http://localhost:8200";
    }
    if (auth == null) {
      auth = new Auth("token", "", new Kubernetes("kubernetes", ""));
    }
    if (requestTimeoutSeconds <= 0) {
      requestTimeoutSeconds = 10;
    }
  }

  public record Auth(String method, String token, Kubernetes kubernetes) {
    public Auth {
      if (method == null || method.isBlank()) {
        method = "token";
      }
      if (token == null) {
        token = "";
      }
      if (kubernetes == null) {
        kubernetes = new Kubernetes("kubernetes", "");
      }
    }
  }

  public record Kubernetes(String mountPath, String role) {
    public Kubernetes {
      if (mountPath == null || mountPath.isBlank()) {
        mountPath = "kubernetes";
      }
      if (role == null) {
        role = "";
      }
    }
  }
}
