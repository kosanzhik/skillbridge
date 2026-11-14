package kz.skillbridge.learning;

import java.time.Instant;

public record LearningResourceDto(Long id, String skill, String title, String provider, String url, String type,
                                  Short level, String language, Boolean free, Instant verifiedAt) {
    public static LearningResourceDto from(LearningResource r) {
        return new LearningResourceDto(r.getId(), r.getSkill().getName(), r.getTitle(), r.getProvider(), r.getUrl(),
                r.getType(), r.getLevel(), r.getLanguage(), r.getFree(), r.getVerifiedAt());
    }
}
