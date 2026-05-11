package io.pravah.playground.vault;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Issues X.509 certificates from Vault PKI (ADR-007 engine; consumed for mTLS in ADR-008).
 *
 * <p>Production uses shorter TTLs, SPIFFE SANs, and cert-manager integration — this playground only proves
 * the API path: {@code pki/issue/&lt;role&gt;} → PEM bundle.
 */
@Service
public class PkiIssueService {

    private final VaultTemplate vault;

    public PkiIssueService(VaultTemplate vault) {
        this.vault = vault;
    }

    /** Issue a leaf certificate for {@code commonName} using role {@code playground}. */
    public Optional<IssuedCertificate> issue(String commonName, String ttl) {
        VaultResponse response =
                vault.write("pki/issue/playground", Map.of("common_name", commonName, "ttl", ttl));
        if (response == null || response.getData() == null) {
            return Optional.empty();
        }
        Map<String, Object> d = response.getData();
        Object cert = d.get("certificate");
        Object key = d.get("private_key");
        Object issuingCa = d.get("issuing_ca");
        if (cert == null || key == null) {
            return Optional.empty();
        }
        return Optional.of(new IssuedCertificate(
                cert.toString(), key.toString(), issuingCa != null ? issuingCa.toString() : ""));
    }

    public record IssuedCertificate(String certificatePem, String privateKeyPem, String issuingCaPem) {}
}
