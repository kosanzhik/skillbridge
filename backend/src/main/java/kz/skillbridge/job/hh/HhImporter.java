package kz.skillbridge.job.hh;

import java.util.List;
import kz.skillbridge.career.Profession;
import kz.skillbridge.career.ProfessionRepository;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.job.JobImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HhImporter {

    private final HhClient hh;
    private final HhProperties props;
    private final ProfessionRepository professions;
    private final JobImportService importService;

    public record Result(String profession, int found, int imported, int failed) {
    }

    public Result importProfession(String slug, Integer limit) {
        Profession p = professions.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Profession"));
        int max = limit == null ? props.perProfessionLimit() : Math.min(limit, 200);
        List<String> ids = hh.searchIds(p.getHhQuery() != null ? p.getHhQuery() : p.getTitle(), max);
        int ok = 0;
        int failed = 0;
        for (String id : ids) {
            try {
                importService.upsert(HhVacancyMapper.map(hh.vacancy(id), p.getId()));
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("hh vacancy {} import failed: {}", id, e.getMessage());
            }
        }
        log.info("hh import {}: found={}, imported={}, failed={}", slug, ids.size(), ok, failed);
        return new Result(slug, ids.size(), ok, failed);
    }

    public List<Result> importAll(Integer limit) {
        return professions.findAllByOrderByTitle().stream().map(p -> importProfession(p.getSlug(), limit)).toList();
    }

    /** Daily refresh at 06:00 Almaty time when enabled and configured. */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Almaty")
    void scheduledImport() {
        if (props.scheduleEnabled() && hh.isConfigured()) {
            importAll(null);
        }
    }
}
