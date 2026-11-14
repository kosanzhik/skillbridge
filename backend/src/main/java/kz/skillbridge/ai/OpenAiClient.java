package kz.skillbridge.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Minimal OpenAI Chat Completions client that only returns schema-constrained JSON
 * (spec §10: structured AI output, validated on the backend).
 */
@Component
public class OpenAiClient {

    private final OpenAiProperties props;
    private final RestClient http;

    public OpenAiClient(OpenAiProperties props) {
        this.props = props;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .build();
    }

    public boolean isEnabled() {
        return props.apiKey() != null && !props.apiKey().isBlank();
    }

    public String model() {
        return props.model();
    }

    /** Returns the raw JSON string produced by the model under a strict JSON schema. */
    public String structuredJson(String systemPrompt, String userContent, String schemaName, Map<String, Object> schema) {
        if (!isEnabled()) {
            throw new AiException("OpenAI API key is not configured");
        }
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", schemaName, "strict", true, "schema", schema)));
        JsonNode resp;
        try {
            resp = http.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiException("OpenAI request failed: " + e.getMessage(), e);
        }
        JsonNode message = resp == null ? null : resp.path("choices").path(0).path("message");
        if (message == null || message.isMissingNode()) {
            throw new AiException("OpenAI returned no choices");
        }
        if (message.hasNonNull("refusal")) {
            throw new AiException("Model refused: " + message.get("refusal").asString());
        }
        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            throw new AiException("OpenAI returned empty content");
        }
        return content.asString();
    }
}
