package kz.skillbridge.job.remote;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kz.skillbridge.career.Profession;
import kz.skillbridge.career.ProfessionRepository;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobImportService;
import kz.skillbridge.job.ProfessionClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Imports international remote vacancies open to Kazakhstan. Search results of both APIs are fuzzy, so every
 * posting must (1) be open to the country and (2) have a title that matches an MVP profession; others are skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteJobsImporter {

    private final RemoteJobsProperties props;
    private final RemotiveSource remotive;
    private final HimalayasSource himalayas;
    private final ProfessionRepository professions;
    private final ProfessionClassifier classifier;
    private final JobImportService importService;
    private final JdbcTemplate jdbc;

    public record Result(String source, String profession, int fetched, int notOpenToCountry, int otherProfession,
                         int imported, int failed) {
    }

    public synchronized List<Result> importAll() {
        List<Result> results = new ArrayList<>();
        for (Profession p : professions.findAllByOrderByTitle()) {
            String query = p.getTitle().toLowerCase();
            results.add(run("REMOTIVE", p, () -> remotive.search(query)));
            results.add(run("HIMALAYAS", p, () -> himalayas.search(query, props.perProfessionLimit())));
        }
        int archived = jdbc.update("update jobs set archived = true where source in ('REMOTIVE','HIMALAYAS') "
                + "and archived = false and fetched_at < ?", java.sql.Timestamp.from(
                Instant.now().minus(props.staleAfterDays(), ChronoUnit.DAYS)));
        log.info("Remote jobs import finished: {} imported, {} stale archived",
                results.stream().mapToInt(Result::imported).sum(), archived);
        return results;
    }

    private Result run(String source, Profession p, java.util.function.Supplier<List<Candidate>> fetch) {
        List<Candidate> candidates;
        try {
            candidates = fetch.get();
        } catch (RuntimeException e) {
            log.warn("{} search for {} failed: {}", source, p.getSlug(), e.getMessage());
            return new Result(source, p.getSlug(), 0, 0, 0, 0, 1);
        }
        int notOpen = 0;
        int other = 0;
        int ok = 0;
        int failed = 0;
        for (Candidate c : candidates) {
            if (!c.openToCountry()) {
                notOpen++;
                continue;
            }
            Optional<Profession> match = classifier.classify(c.classifyText());
            if (match.isEmpty() || !match.get().getId().equals(p.getId())) {
                other++; // either irrelevant, or will be imported under its own profession's search
                continue;
            }
            if (c.job().externalId() == null || c.job().url() == null || c.job().title() == null) {
                failed++;
                continue;
            }
            try {
                importService.upsert(withProfession(c.job(), p.getId()));
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("{} job {} import failed: {}", source, c.job().externalId(), e.getMessage());
            }
        }
        return new Result(source, p.getSlug(), candidates.size(), notOpen, other, ok, failed);
    }

    private static ImportedJob withProfession(ImportedJob j, Long professionId) {
        return new ImportedJob(j.source(), j.externalId(), professionId, j.title(), j.company(), j.location(),
                j.salaryFrom(), j.salaryTo(), j.salaryCurrency(), j.salaryGross(), j.experience(), j.employment(),
                j.schedule(), j.descriptionText(), j.url(), j.publishedAt(), j.archived(), j.explicitSkills(),
                j.explicitPreferredSkills(), j.salaryPeriod(), j.salaryText(), j.remote());
    }

    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Almaty")
    void daily() {
        if (props.enabled() && props.scheduleEnabled()) {
            importAll();
        }
    }

    /** For demos: fill an empty database right after startup, in the background. */
    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        if (!props.enabled() || !props.importOnStartup()) {
            return;
        }
        Integer existing = jdbc.queryForObject(
                "select count(*) from jobs where source in ('REMOTIVE','HIMALAYAS') and archived = false", Integer.class);
        if (existing != null && existing == 0) {
            Thread.ofVirtual().name("remote-jobs-initial-import").start(() -> {
                try {
                    importAll();
                } catch (RuntimeException e) {
                    log.warn("Initial remote jobs import failed: {}", e.getMessage());
                }
            });
        }
    }
}
