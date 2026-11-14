package kz.skillbridge.job.telegram;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kz.skillbridge.job.ImportedJob;
import kz.skillbridge.job.JobImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Template parser first (exact), AI only for free-form posts when configured. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramIngestService {

    public enum Outcome { IMPORTED_TEMPLATE, IMPORTED_AI, SKIPPED_NOT_VACANCY, SKIPPED_NO_TEMPLATE, FAILED }

    private final TelegramPostParser templateParser;
    private final AiTelegramPostParser aiParser;
    private final JobImportService importService;

    public Outcome ingest(TelegramPost post) {
        try {
            Optional<ImportedJob> job = templateParser.parse(post);
            if (job.isPresent()) {
                importService.upsert(job.get());
                return Outcome.IMPORTED_TEMPLATE;
            }
            if (!aiParser.isEnabled()) {
                return Outcome.SKIPPED_NO_TEMPLATE;
            }
            job = aiParser.parse(post);
            if (job.isEmpty()) {
                return Outcome.SKIPPED_NOT_VACANCY;
            }
            importService.upsert(job.get());
            return Outcome.IMPORTED_AI;
        } catch (RuntimeException e) {
            log.warn("Telegram post {}:{} ingest failed: {}", post.channel(), post.messageId(), e.getMessage());
            return Outcome.FAILED;
        }
    }

    public Map<Outcome, Integer> ingestAll(List<TelegramPost> posts) {
        Map<Outcome, Integer> summary = new EnumMap<>(Outcome.class);
        for (TelegramPost p : posts) {
            summary.merge(ingest(p), 1, Integer::sum);
        }
        return summary;
    }
}
