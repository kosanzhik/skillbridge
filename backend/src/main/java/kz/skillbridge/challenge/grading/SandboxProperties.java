package kz.skillbridge.challenge.grading;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("skillbridge.sandbox")
public record SandboxProperties(String username, String password) {
}
