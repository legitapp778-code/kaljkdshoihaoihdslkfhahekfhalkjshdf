package com.totemena.elite.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter @Setter
public class JwtConfig {
    private String secret;
    private long accessExpiryMs;
    private long refreshExpiryMs;
}
