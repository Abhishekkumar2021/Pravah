package io.pravah.runnerservice.infrastructure.pki;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pravah.runner.pki")
public class RunnerPkiProperties {

  private boolean enabled;
  private String issuePath = "pki/issue/runner";
  private String ttl = "168h";
  private String spiffeTrustDomain = "pravah.local";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getIssuePath() {
    return issuePath;
  }

  public void setIssuePath(String issuePath) {
    this.issuePath = issuePath;
  }

  public String getTtl() {
    return ttl;
  }

  public void setTtl(String ttl) {
    this.ttl = ttl;
  }

  public String getSpiffeTrustDomain() {
    return spiffeTrustDomain;
  }

  public void setSpiffeTrustDomain(String spiffeTrustDomain) {
    this.spiffeTrustDomain = spiffeTrustDomain;
  }
}
