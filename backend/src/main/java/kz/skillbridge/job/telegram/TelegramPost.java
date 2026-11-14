package kz.skillbridge.job.telegram;

import java.time.Instant;
import java.util.List;

/** A channel post from the Bot API or a Telegram Desktop export. */
public record TelegramPost(String channel, long messageId, String text, List<String> links, Instant publishedAt) {
}
