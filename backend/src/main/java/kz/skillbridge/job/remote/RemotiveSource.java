package kz.skillbridge.job.remote;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kz.skillbridge.job.Html;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** https://remotive.com/api/remote-jobs — open API; credit Remotive and link to the job page. */
@Component
public class RemotiveSource {

    private final RemoteJobClient client;

    public RemotiveSource(RemoteJobsProperties props) {
        this.client = new RemoteJobClient(props.remotiveBaseUrl(), props.userAgent());
    }

    /** Raw search results; filtering by region/profession happens in the importer. */
    public List<Candidate> search(String query) {
        JsonNode res = client.get(u -> u.path("/api/remote-jobs").queryParam("search", query).build());
        List<Candidate> out = new ArrayList<>();
        for (JsonNode j : res.path("jobs")) {
            String location = RemoteJobClient.text(j, "candidate_required_location");
            List<String> tags = new ArrayList<>();
            j.path("tags").forEach(t -> tags.add(t.asString()));
            ImportedJob job = new ImportedJob(JobSource.REMOTIVE, j.path("id").asString(), null,
                    RemoteJobClient.text(j, "title"), RemoteJobClient.text(j, "company_name"),
                    "Удалённо · " + (location == null ? "регион не указан" : location),
                    null, null, null, null,
                    null, employment(RemoteJobClient.text(j, "job_type")), "Удалённая работа",
                    Html.toText(RemoteJobClient.text(j, "description")), RemoteJobClient.text(j, "url"),
                    parseDate(RemoteJobClient.text(j, "publication_date")), false, tags, List.of(),
                    null, RemoteJobClient.text(j, "salary"), true);
            out.add(new Candidate(job, RemoteJobClient.text(j, "title"), isOpenTo(location)));
        }
        return out;
    }

    /** Remotive gives free text like "Worldwide", "USA", "LATAM, Europe". */
    static boolean isOpenTo(String location) {
        if (location == null) {
            return false;
        }
        String l = location.toLowerCase(Locale.ROOT);
        return l.contains("worldwide") || l.contains("anywhere") || l.contains("kazakhstan")
                || l.contains("central asia") || l.contains("cis");
    }

    static String employment(String jobType) {
        if (jobType == null) {
            return null;
        }
        return switch (jobType) {
            case "full_time" -> "Полная занятость";
            case "part_time" -> "Частичная занятость";
            case "contract" -> "Контракт";
            case "freelance" -> "Фриланс";
            case "internship" -> "Стажировка";
            default -> jobType;
        };
    }

    static Instant parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC); // Remotive dates carry no offset
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
