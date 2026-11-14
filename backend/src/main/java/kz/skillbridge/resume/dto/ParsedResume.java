package kz.skillbridge.resume.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Structured CV data. Produced by an extractor, shown to the user for review,
 * and sent back (possibly edited) on confirm. Dates are "YYYY-MM"; endDate null means current/unknown.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedResume(
        @Size(max = 255) String fullName,
        @Size(max = 255) String headline,
        @Size(max = 255) String location,
        @Size(max = 20) List<String> languages,
        @Size(max = 50) List<@Valid Experience> experiences,
        @Size(max = 20) List<@Valid Education> educations,
        @Size(max = 50) List<@Valid Project> projects,
        @Size(max = 50) List<@Valid Certification> certifications,
        @Size(max = 150) List<@Valid ParsedSkill> skills) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Experience(String company, String title, String startDate, String endDate, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(String institution, String degree, String field, Integer startYear, Integer endYear) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String name, String description, List<String> skills) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Certification(String name, String issuer, Integer year) {
    }

    /**
     * {@code name}/{@code level}/{@code evidence} come from the CV; {@code skillId}/{@code skillName} are the
     * catalog match (null = not in catalog, will not be saved). On confirm the client may set skillId explicitly.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParsedSkill(String name, Integer level, String evidence, Long skillId, String skillName) {
    }
}
