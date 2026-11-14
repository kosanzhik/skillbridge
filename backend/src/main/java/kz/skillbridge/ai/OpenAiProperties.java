package kz.skillbridge.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("skillbridge.openai")
public record OpenAiProperties(String apiKey, String model, String baseUrl, int timeoutSeconds) {
}
