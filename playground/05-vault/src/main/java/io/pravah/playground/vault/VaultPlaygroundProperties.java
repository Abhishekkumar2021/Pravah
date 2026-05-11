package io.pravah.playground.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "playground.vault")
public record VaultPlaygroundProperties(String uri, String token) {}
