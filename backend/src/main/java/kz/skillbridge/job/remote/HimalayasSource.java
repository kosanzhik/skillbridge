package kz.skillbridge.job.remote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import kz.skillbridge.job.Html;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** https://himalayas.app/jobs/api/search — open API; country filter limits results to jobs open to that country. */
@Component
public class HimalayasSource {

    static final int PAGE_SIZE = 20;
    private static final int MANY_COUNTRIES = 30;

    private final RemoteJobClient client;
    private final String country;

    public HimalayasSource(RemoteJobsProperties props) {
        this.client = new RemoteJobClient(props.himalayasBaseUrl(), props.userAgent());
        this.country = props.country();
    }

    public List<Candidate> search(String query, int limit) {
        List<Candidate> out = new ArrayList<>();
        for (int page = 1; out.size() < limit; page++) {
            int p = page;
            JsonNode res = client.get(u -> u.path("/jobs/api/search").queryParam("q", query)
                    .queryParam("country", country).queryParam("page", p).build());
            JsonNode jobs = res.path("jobs");
            if (!jobs.isArray() || jobs.isEmpty()) {
                break;
            }
            for (JsonNode j : jobs) {
                out.add(map(j));
            }
            if (jobs.size() < PAGE_SIZE) {
                break;
            }
        }
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    Candidate map(JsonNode j) {
        String guid = RemoteJobClient.text(j, "guid");
        List<String> countries = new ArrayList<>();
        j.path("locationRestrictions").forEach(c -> countries.add(c.asString()));
        boolean open = countries.isEmpty() || countries.stream().anyMatch(c -> c.equalsIgnoreCase(country));
        String region = countries.isEmpty() || countries.size() >= MANY_COUNTRIES ? "Worldwide"
                : String.join(", ", countries.subList(0, Math.min(3, countries.size())))
                        + (countries.size() > 3 ? " и др." : "");
        Integer min = RemoteJobClient.positiveInt(j, "minSalary");
        Integer max = RemoteJobClient.positiveInt(j, "maxSalary");
        boolean hasSalary = min != null || max != null;
        List<String> seniority = new ArrayList<>();
        j.path("seniority").forEach(s -> seniority.add(s.asString()));
        long expiry = j.path("expiryDate").asLong(0);
        long pub = j.path("pubDate").asLong(0);

        ImportedJob job = new ImportedJob(JobSource.HIMALAYAS, guid == null ? null : sha1(guid), null,
                RemoteJobClient.text(j, "title"), RemoteJobClient.text(j, "companyName"), "Удалённо · " + region,
                min, max, hasSalary ? RemoteJobClient.text(j, "currency") : null, null,
                seniority.isEmpty() ? null : String.join(", ", seniority), RemoteJobClient.text(j, "employmentType"),
                "Удалённая работа", Html.toText(RemoteJobClient.text(j, "description")), guid,
                pub > 0 ? Instant.ofEpochSecond(pub) : null,
                expiry > 0 && Instant.ofEpochSecond(expiry).isBefore(Instant.now()),
                List.of(), List.of(), hasSalary ? period(RemoteJobClient.text(j, "salaryPeriod")) : null, null, true);
        // classify by title only: Himalayas categories are too loose ("Finance Manager" tagged as analyst)
        return new Candidate(job, RemoteJobClient.text(j, "title"), open);
    }

    static String period(String p) {
        if (p == null) {
            return null;
        }
        return switch (p.toLowerCase(Locale.ROOT)) {
            case "annual", "yearly", "year" -> "YEAR";
            case "monthly", "month" -> "MONTH";
            case "weekly", "week" -> "WEEK";
            case "daily", "day" -> "DAY";
            case "hourly", "hour" -> "HOUR";
            default -> null;
        };
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
