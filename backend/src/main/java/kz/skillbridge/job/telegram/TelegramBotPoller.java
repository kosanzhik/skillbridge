package kz.skillbridge.job.telegram;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kz.skillbridge.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Receives new channel posts via Bot API getUpdates. The Bot API cannot read history — use the export
 * import for older posts. Offset is kept in memory; re-processing after restart is safe (upsert).
 */
@Slf4j
@Component
public class TelegramBotPoller {

    private final TelegramProperties props;
    private final TelegramIngestService ingest;
    private final RestClient http;
    private long offset;

    public TelegramBotPoller(TelegramProperties props, TelegramIngestService ingest) {
        this.props = props;
        this.ingest = ingest;
        this.http = RestClient.builder().baseUrl(props.apiBaseUrl()).build();
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    void scheduledPoll() {
        if (props.pollingEnabled() && props.isConfigured()) {
            try {
                pollOnce();
            } catch (RuntimeException e) {
                log.warn("Telegram polling failed: {}", e.getMessage());
            }
        }
    }

    public synchronized Map<TelegramIngestService.Outcome, Integer> pollOnce() {
        if (!props.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TG_NOT_CONFIGURED",
                    "TELEGRAM_BOT_TOKEN and TELEGRAM_CHANNEL are not configured");
        }
        JsonNode res;
        try {
            res = http.get().uri(u -> u.path("/bot{token}/getUpdates")
                            .queryParam("offset", offset)
                            .queryParam("timeout", 0)
                            .queryParam("allowed_updates", "[\"channel_post\",\"edited_channel_post\"]")
                            .build(props.botToken()))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TG_UNAVAILABLE", "Telegram request failed");
        }
        List<TelegramPost> posts = new ArrayList<>();
        for (JsonNode u : res.path("result")) {
            offset = Math.max(offset, u.path("update_id").asLong() + 1);
            JsonNode msg = u.hasNonNull("channel_post") ? u.get("channel_post") : u.path("edited_channel_post");
            if (msg.isMissingNode() || !isOurChannel(msg.path("chat"))) {
                continue;
            }
            TelegramPost post = toPost(msg);
            if (post != null) {
                posts.add(post);
            }
        }
        return ingest.ingestAll(posts);
    }

    private boolean isOurChannel(JsonNode chat) {
        String key = props.channelKey();
        return key.equalsIgnoreCase(chat.path("username").asString("")) || key.equals(chat.path("id").asString(""));
    }

    private TelegramPost toPost(JsonNode msg) {
        boolean caption = !msg.hasNonNull("text");
        String text = caption ? msg.path("caption").asString(null) : msg.get("text").asString();
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> links = new ArrayList<>();
        for (JsonNode e : msg.path(caption ? "caption_entities" : "entities")) {
            String type = e.path("type").asString("");
            if ("text_link".equals(type) && e.hasNonNull("url")) {
                links.add(e.get("url").asString());
            } else if ("url".equals(type)) {
                int off = e.path("offset").asInt();
                int len = e.path("length").asInt();
                if (off >= 0 && off + len <= text.length()) {
                    links.add(text.substring(off, off + len)); // Bot API offsets are UTF-16, like Java strings
                }
            }
        }
        JsonNode chat = msg.path("chat");
        String channel = chat.hasNonNull("username") ? chat.get("username").asString() : chat.path("id").asString();
        return new TelegramPost(channel, msg.path("message_id").asLong(), text, links,
                Instant.ofEpochSecond(msg.path("date").asLong()));
    }
}
