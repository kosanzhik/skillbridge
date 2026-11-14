package kz.skillbridge.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("skillbridge.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
