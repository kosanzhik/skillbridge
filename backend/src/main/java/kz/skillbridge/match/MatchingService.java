package kz.skillbridge.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kz.skillbridge.career.CareerGoal;
import kz.skillbridge.career.CareerService;
import kz.skillbridge.career.ProfessionRepository;
import kz.skillbridge.career.dto.CareerGoalDto;
import kz.skillbridge.job.Job;
import kz.skillbridge.job.JobRepository;
import kz.skillbridge.job.JobSkill;
import kz.skillbridge.job.RequirementType;
import kz.skillbridge.job.dto.JobSummary;
import kz.skillbridge.match.dto.JobMatchDto;
import kz.skillbridge.match.dto.MatchesResponse;
import kz.skillbridge.match.dto.SkillMatch;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.SkillProfileService;
import kz.skillbridge.skill.SkillProfileService.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rule-based, explainable matching (spec §4 step 5, §10 "Matching Engine").
 * Not a probability of being hired.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    static final int WEIGHT_REQUIRED = 2;
    static final int WEIGHT_PREFERRED = 1;
    static final int DEFAULT_REQUIRED_LEVEL = 3;
    static final int MAX_JOBS = 200;

    static final String METHODOLOGY = "score = Σ(weight × credit) / Σ(weight) × 100, где weight = 2 для обязательного "
            + "и 1 для желательного навыка вакансии, credit = min(ваш уровень / требуемый уровень, 1). "
            + "Требуемый уровень берётся из базовых требований профессии (по умолчанию 3/5). "
            + "Ваш уровень: результат проверки (ASSESSED/VERIFIED), если он есть, иначе максимум из CV и самооценки.";
    static final String DISCLAIMER = "Это оценка соответствия профиля требованиям вакансии, а не вероятность трудоустройства.";

    private final CareerService careerService;
    private final ProfessionRepository professions;
    private final JobRepository jobs;
    private final SkillProfileService skillProfile;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    @Transactional
    public MatchesResponse matchesForPrimaryGoal(Long userId, int limit) {
        CareerGoal goal = careerService.requirePrimaryGoal(userId);
        Long professionId = goal.getProfession().getId();

        List<Job> candidates = jobs.findActiveByProfession(professionId, PageRequest.of(0, MAX_JOBS));
        Map<Long, List<JobSkill>> skillsByJob = candidates.isEmpty() ? Map.of()
                : jobs.findSkillsForJobs(candidates.stream().map(Job::getId).toList()).stream()
                        .collect(Collectors.groupingBy(js -> js.getId().getJobId()));
        Map<Long, Integer> baseline = professions.findRequirements(professionId).stream()
                .collect(Collectors.toMap(ps -> ps.getSkill().getId(), ps -> (int) ps.getRequiredLevel()));
        Map<Long, Level> user = skillProfile.effectiveLevels(userId);

        List<JobMatchDto> results = new ArrayList<>();
        for (Job job : candidates) {
            results.add(score(job, skillsByJob.getOrDefault(job.getId(), List.of()), baseline, user));
        }
        persist(userId, results);

        results.sort(Comparator.comparing((JobMatchDto m) -> m.score() == null ? -1 : m.score()).reversed()
                .thenComparing(m -> m.job().publishedAt(), Comparator.nullsLast(Comparator.reverseOrder())));
        var p = goal.getProfession();
        return new MatchesResponse(new CareerGoalDto.ProfessionRef(p.getId(), p.getSlug(), p.getTitle()),
                candidates.size(), METHODOLOGY, DISCLAIMER, results.stream().limit(limit).toList());
    }

    static JobMatchDto score(Job job, List<JobSkill> reqs, Map<Long, Integer> baseline, Map<Long, Level> user) {
        List<SkillMatch> matched = new ArrayList<>();
        List<SkillMatch> partial = new ArrayList<>();
        List<SkillMatch> missing = new ArrayList<>();
        double total = 0;
        double earned = 0;
        for (JobSkill js : reqs) {
            Long skillId = js.getSkill().getId();
            int weight = js.getRequirementType() == RequirementType.REQUIRED ? WEIGHT_REQUIRED : WEIGHT_PREFERRED;
            int required = baseline.getOrDefault(skillId, DEFAULT_REQUIRED_LEVEL);
            Level have = user.get(skillId);
            int userLevel = have == null ? 0 : have.level();
            double credit = Math.min((double) userLevel / required, 1.0);
            SkillMatch m = new SkillMatch(skillId, js.getSkill().getName(), js.getRequirementType(), weight, required,
                    userLevel, have == null ? null : have.source(), have != null && have.proven(), round(credit));
            (credit >= 1.0 ? matched : credit > 0 ? partial : missing).add(m);
            total += weight;
            earned += weight * credit;
        }
        Comparator<SkillMatch> order = Comparator.comparing(SkillMatch::requirement).thenComparing(SkillMatch::skill);
        matched.sort(order);
        partial.sort(order);
        missing.sort(order);
        Double score = total == 0 ? null : round(earned / total * 100);
        return new JobMatchDto(JobSummary.from(job), score, matched, partial, missing);
    }

    private void persist(Long userId, List<JobMatchDto> results) {
        List<Object[]> rows = results.stream().filter(r -> r.score() != null)
                .map(r -> new Object[]{userId, r.job().id(), r.score(), json.writeValueAsString(r)})
                .toList();
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into job_matches (user_id, job_id, score, explanation_json, computed_at)
                    values (?, ?, ?, ?, now())
                    on conflict (user_id, job_id) do update
                    set score = excluded.score, explanation_json = excluded.explanation_json, computed_at = now()
                    """, rows);
        }
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
