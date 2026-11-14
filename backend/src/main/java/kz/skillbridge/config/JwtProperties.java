package kz.skillbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("skillbridge.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
