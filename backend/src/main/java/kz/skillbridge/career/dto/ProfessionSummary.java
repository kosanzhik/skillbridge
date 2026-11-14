package kz.skillbridge.career.dto;

import kz.skillbridge.career.Profession;

public record ProfessionSummary(Long id, String slug, String title, String description, long requirementCount) {
    public static ProfessionSummary from(Profession p, long requirementCount) {
        return new ProfessionSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), requirementCount);
    }
}
