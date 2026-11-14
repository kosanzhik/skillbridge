package kz.skillbridge.user.dto;

import java.time.LocalDate;
import java.util.List;
import kz.skillbridge.user.DataSource;
import kz.skillbridge.user.Education;
import kz.skillbridge.user.Experience;

public record BackgroundResponse(List<ExperienceDto> experiences, List<EducationDto> educations) {

    public record ExperienceDto(Long id, String company, String title, LocalDate startDate, LocalDate endDate,
                                String description, DataSource source) {
        public static ExperienceDto from(Experience e) {
            return new ExperienceDto(e.getId(), e.getCompany(), e.getTitle(), e.getStartDate(), e.getEndDate(),
                    e.getDescription(), e.getSource());
        }
    }

    public record EducationDto(Long id, String institution, String degree, String field, Short startYear,
                               Short endYear, DataSource source) {
        public static EducationDto from(Education e) {
            return new EducationDto(e.getId(), e.getInstitution(), e.getDegree(), e.getField(), e.getStartYear(),
                    e.getEndYear(), e.getSource());
        }
    }
}
