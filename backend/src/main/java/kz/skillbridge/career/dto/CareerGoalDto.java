package kz.skillbridge.career.dto;

import java.time.Instant;
import kz.skillbridge.career.CareerGoal;

public record CareerGoalDto(Long id, ProfessionRef profession, boolean primary, Instant createdAt) {

    public record ProfessionRef(Long id, String slug, String title) {
    }

    public static CareerGoalDto from(CareerGoal g) {
        var p = g.getProfession();
        return new CareerGoalDto(g.getId(), new ProfessionRef(p.getId(), p.getSlug(), p.getTitle()), g.isPrimary(),
                g.getCreatedAt());
    }
}
