package kz.skillbridge.job;

import java.time.Instant;
import java.util.List;

/** Source-independent vacancy data. Null means "not stated in the source". */
public record ImportedJob(
        JobSource source,
        String externalId,
        Long professionId,
        String title,
        String company,
        String location,
        Integer salaryFrom,
        Integer salaryTo,
        String salaryCurrency,
        Boolean salaryGross,
        String experience,
        String employment,
        String schedule,
        String descriptionText,
        String url,
        Instant publishedAt,
        boolean archived,
        List<String> explicitSkills,
        List<String> explicitPreferredSkills,
        String salaryPeriod,
        String salaryText,
        boolean remote) {
}
