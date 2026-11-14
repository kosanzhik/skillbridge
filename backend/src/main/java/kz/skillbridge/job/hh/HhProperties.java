package kz.skillbridge.job.hh;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * hh.ru API requires an application token for vacancy search (anonymous access returns 403).
 * Either set {@code appToken} directly or {@code clientId}/{@code clientSecret} to obtain one.
 */
@ConfigurationProperties("skillbridge.hh")
public record HhProperties(
        String userAgent,
        String baseUrl,
        String tokenUrl,
        String appToken,
        String clientId,
        String clientSecret,
        String area,
        int perProfessionLimit,
        boolean scheduleEnabled) {

    public boolean isConfigured() {
        return notBlank(appToken) || (notBlank(clientId) && notBlank(clientSecret));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
