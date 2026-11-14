package kz.skillbridge.job.hh;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import kz.skillbridge.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Official hh.ru API client (https://api.hh.ru/openapi/redoc). */
@Slf4j
@Component
public class HhClient {

    private final HhProperties props;
    private final RestClient http;
    private volatile String token;

    public HhClient(HhProperties props) {
        this.props = props;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("HH-User-Agent", props.userAgent())
                .defaultHeader("User-Agent", props.userAgent())
                .build();
        this.token = blankToNull(props.appToken());
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    /** Returns vacancy ids from search results, newest first, up to {@code limit}. */
    public List<String> searchIds(String text, int limit) {
        List<String> ids = new ArrayList<>();
        int perPage = Math.min(100, Math.max(1, limit));
        for (int page = 0; ids.size() < limit; page++) {
            int p = page;
            JsonNode res = get(uri -> uri.path("/vacancies")
                    .queryParam("text", text)
                    .queryParam("area", props.area())
                    .queryParam("per_page", perPage)
                    .queryParam("page", p)
                    .queryParam("order_by", "publication_time")
                    .build());
            JsonNode items = res.path("items");
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            for (JsonNode item : items) {
                if (ids.size() < limit) {
                    ids.add(item.path("id").asString());
                }
            }
            if (page + 1 >= res.path("pages").asInt(0)) {
                break;
            }
        }
        return ids;
    }

    public JsonNode vacancy(String id) {
        return get(uri -> uri.path("/vacancies/{id}").build(id));
    }

    private JsonNode get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        try {
            return http.get().uri(uri).header("Authorization", "Bearer " + token())
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "HH_UNAVAILABLE", "hh.ru request failed: " + e.getMessage());
        }
    }

    private String token() {
        if (token != null) {
            return token;
        }
        if (!props.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "HH_NOT_CONFIGURED",
                    "hh.ru API credentials are not configured (HH_APP_TOKEN or HH_CLIENT_ID/HH_CLIENT_SECRET)");
        }
        synchronized (this) {
            if (token == null) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", "client_credentials");
                form.add("client_id", props.clientId());
                form.add("client_secret", props.clientSecret());
                JsonNode res = http.post().uri(props.tokenUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form).retrieve().body(JsonNode.class);
                String t = res == null ? null : blankToNull(res.path("access_token").asString(null));
                if (t == null) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "HH_AUTH_FAILED", "hh.ru did not return a token");
                }
                log.info("Obtained hh.ru application token; set HH_APP_TOKEN to reuse it");
                token = t;
            }
            return token;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
