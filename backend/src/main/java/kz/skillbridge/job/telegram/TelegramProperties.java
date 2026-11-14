package kz.skillbridge.job.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Team channel ingestion. {@code channel} is the public username (without @) or numeric chat id (-100...).
 * The bot must be an administrator of the channel to receive channel_post updates.
 */
@ConfigurationProperties("skillbridge.telegram")
public record TelegramProperties(String botToken, String channel, String apiBaseUrl, boolean pollingEnabled) {

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && channel != null && !channel.isBlank();
    }

    public String channelKey() {
        return channel == null ? null : channel.strip().replaceFirst("^@", "");
    }
}
