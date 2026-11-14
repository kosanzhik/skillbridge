package kz.skillbridge.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("skillbridge.storage")
public record StorageProperties(String path) {
}
