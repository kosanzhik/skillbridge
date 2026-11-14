package kz.skillbridge.job.remote;

import java.time.Duration;
import java.util.function.Function;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

/** Tiny GET-JSON client shared by the remote job sources. */
final class RemoteJobClient {

    private final RestClient http;

    RemoteJobClient(String baseUrl, String userAgent) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", userAgent).build();
    }

    JsonNode get(Function<UriBuilder, java.net.URI> uri) {
        return http.get().uri(uri).retrieve().body(JsonNode.class);
    }

    static String text(JsonNode n, String field) {
        JsonNode f = n == null ? null : n.get(field);
        if (f == null || f.isNull() || f.isMissingNode()) {
            return null;
        }
        String s = f.asString().strip();
        return s.isEmpty() ? null : s;
    }

    static Integer positiveInt(JsonNode n, String field) {
        JsonNode f = n == null ? null : n.get(field);
        if (f == null || !f.isNumber()) {
            return null;
        }
        long v = f.asLong();
        return v > 0 && v < Integer.MAX_VALUE ? (int) v : null;
    }
}
