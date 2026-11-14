package kz.skillbridge.job;

import java.time.Instant;
import java.util.Map;
import kz.skillbridge.career.ProfessionRepository;
import kz.skillbridge.skill.Skill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobImportService {

    private final JobRepository jobs;
    private final ProfessionRepository professions;
    private final ProfessionClassifier classifier;
    private final JobSkillExtractor extractor;

    /** Inserts or updates a vacancy by (source, externalId) and re-extracts its skill requirements. */
    @Transactional
    public Job upsert(ImportedJob in) {
        Job job = jobs.findBySourceAndExternalId(in.source(), in.externalId()).orElseGet(Job::new);
        job.setSource(in.source());
        job.setExternalId(in.externalId());
        job.setProfession(in.professionId() != null
                ? professions.getReferenceById(in.professionId())
                : classifier.classify(in.title()).orElse(null));
        job.setTitle(truncate(in.title(), 255));
        job.setCompany(truncate(in.company(), 255));
        job.setLocation(truncate(in.location(), 255));
        job.setSalaryFrom(in.salaryFrom());
        job.setSalaryTo(in.salaryTo());
        job.setSalaryCurrency(truncate(in.salaryCurrency(), 10));
        job.setSalaryGross(in.salaryGross());
        job.setExperience(truncate(in.experience(), 100));
        job.setEmployment(truncate(in.employment(), 100));
        job.setSchedule(truncate(in.schedule(), 100));
        job.setDescription(in.descriptionText());
        job.setUrl(truncate(in.url(), 500));
        job.setPublishedAt(in.publishedAt());
        job.setArchived(in.archived());
        job.setFetchedAt(Instant.now());
        job = jobs.save(job);

        // delete old rows before inserting new ones with the same composite keys
        job.getSkills().clear();
        jobs.flush();
        Map<Skill, RequirementType> reqs = extractor.extract(in.explicitSkills(), in.descriptionText());
        if (in.explicitPreferredSkills() != null) {
            extractor.extract(in.explicitPreferredSkills(), null)
                    .keySet().forEach(s -> reqs.putIfAbsent(s, RequirementType.PREFERRED));
        }
        for (Map.Entry<Skill, RequirementType> e : reqs.entrySet()) {
            job.getSkills().add(new JobSkill(job, e.getKey(), e.getValue()));
        }
        return job;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
