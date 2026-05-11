package io.pravah.playground.vault;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

@Configuration
@EnableConfigurationProperties(VaultPlaygroundProperties.class)
public class VaultClientConfiguration {

    @Bean
    VaultTemplate vaultTemplate(VaultPlaygroundProperties props) {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(props.uri()));
        ClientAuthentication auth = new TokenAuthentication(props.token());
        return new VaultTemplate(endpoint, auth);
    }
}
