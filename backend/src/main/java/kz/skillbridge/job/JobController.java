package kz.skillbridge.job;

import java.util.Comparator;
import java.util.List;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.job.dto.JobDetail;
import kz.skillbridge.job.dto.JobSummary;
import kz.skillbridge.job.hh.HhImporter;
import kz.skillbridge.skill.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobs;
    private final HhImporter hhImporter;

    @GetMapping("/jobs/{id}")
    @Transactional(readOnly = true)
    public JobDetail job(@PathVariable Long id) {
        Job j = jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job"));
        List<JobDetail.Requirement> reqs = j.getSkills().stream()
                .sorted(Comparator.comparing(JobSkill::getRequirementType).thenComparing(s -> s.getSkill().getName()))
                .map(s -> new JobDetail.Requirement(SkillDto.from(s.getSkill()), s.getRequirementType()))
                .toList();
        return new JobDetail(JobSummary.from(j), j.getEmployment(), j.getDescription(), reqs);
    }

    /** Admin: import vacancies from hh.ru for one profession, or all when {@code profession} is omitted. */
    @PostMapping("/admin/jobs/hh/import")
    public List<HhImporter.Result> importHh(@RequestParam(required = false) String profession,
                                            @RequestParam(required = false) Integer limit) {
        return profession == null ? hhImporter.importAll(limit) : List.of(hhImporter.importProfession(profession, limit));
    }
}
