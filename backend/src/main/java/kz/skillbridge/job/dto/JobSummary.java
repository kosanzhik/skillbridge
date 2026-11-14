package kz.skillbridge.job.dto;

import java.time.Instant;
import kz.skillbridge.job.Job;
import kz.skillbridge.job.JobSource;

/** Null salary/experience fields mean "не указано" in the source — the UI must not fill them in. */
public record JobSummary(Long id, JobSource source, String title, String company, String location,
                         Integer salaryFrom, Integer salaryTo, String salaryCurrency, Boolean salaryGross,
                         String experience, String schedule, String url, Instant publishedAt) {
    public static JobSummary from(Job j) {
        return new JobSummary(j.getId(), j.getSource(), j.getTitle(), j.getCompany(), j.getLocation(),
                j.getSalaryFrom(), j.getSalaryTo(), j.getSalaryCurrency(), j.getSalaryGross(), j.getExperience(),
                j.getSchedule(), j.getUrl(), j.getPublishedAt());
    }
}
