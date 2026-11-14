package kz.skillbridge.job.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kz.skillbridge.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads Telegram Desktop "Export chat history" result.json (machine-readable JSON format). */
@Component
@RequiredArgsConstructor
public class TelegramExportReader {

    private final JsonMapper json;
    private final TelegramProperties props;

    public List<TelegramPost> read(InputStream in) {
        JsonNode root;
        try {
            root = json.readTree(in);
        } catch (JacksonException e) {
            throw invalid();
        }
        if (root == null || !root.path("messages").isArray()) {
            throw invalid();
        }
        String channel = props.channelKey() != null && !props.channelKey().isBlank() ? props.channelKey()
                : root.hasNonNull("id") ? "-100" + root.get("id").asString() : "unknown";
        List<TelegramPost> posts = new ArrayList<>();
        for (JsonNode m : root.path("messages")) {
            if (!"message".equals(m.path("type").asString(""))) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            List<String> links = new ArrayList<>();
            JsonNode t = m.path("text");
            if (t.isString()) {
                text.append(t.asString());
            } else if (t.isArray()) {
                for (JsonNode part : t) {
                    if (part.isString()) {
                        text.append(part.asString());
                    } else {
                        String partText = part.path("text").asString("");
                        text.append(partText);
                        String type = part.path("type").asString("");
                        if ("text_link".equals(type) && part.hasNonNull("href")) {
                            links.add(part.get("href").asString());
                        } else if ("link".equals(type)) {
                            links.add(partText);
                        }
                    }
                }
            }
            if (text.isEmpty()) {
                continue;
            }
            Instant date = m.hasNonNull("date_unixtime")
                    ? Instant.ofEpochSecond(Long.parseLong(m.get("date_unixtime").asString())) : null;
            posts.add(new TelegramPost(channel, m.path("id").asLong(), text.toString(), links, date));
        }
        return posts;
    }

    public List<TelegramPost> read(byte[] bytes) {
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            return read(in);
        } catch (IOException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "TG_EXPORT_INVALID",
                "Expected Telegram Desktop export result.json (JSON format)");
    }
}
