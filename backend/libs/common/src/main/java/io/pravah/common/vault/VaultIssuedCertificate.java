package io.pravah.common.vault;

import java.util.Objects;

/** PEM bundle returned by Vault PKI {@code pki/issue/<role>} (ADR-007/008). */
public record VaultIssuedCertificate(
    String certificatePem, String privateKeyPem, String issuingCaPem) {

  public VaultIssuedCertificate {
    Objects.requireNonNull(certificatePem, "certificatePem");
    Objects.requireNonNull(privateKeyPem, "privateKeyPem");
    Objects.requireNonNull(issuingCaPem, "issuingCaPem");
  }
}
