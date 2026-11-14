package kz.skillbridge.job.remote;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * International remote job boards with open APIs. Both ask for attribution and a link to the original posting;
 * Remotive also asks not to poll more than a few times a day — the importer runs once a day.
 */
@ConfigurationProperties("skillbridge.remote-jobs")
public record RemoteJobsProperties(
        boolean enabled,
        boolean scheduleEnabled,
        boolean importOnStartup,
        String userAgent,
        String remotiveBaseUrl,
        String himalayasBaseUrl,
        String country,
        boolean onlyOpenToCountry,
        int perProfessionLimit,
        int staleAfterDays) {
}
