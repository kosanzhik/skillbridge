package kz.skillbridge.gap.dto;

import kz.skillbridge.gap.GapStatus;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.dto.SkillDto;

/**
 * {@code demandShare} — share of analysed vacancies mentioning the skill (null without market data).
 * {@code importanceSource} — MARKET (from vacancies) or BASELINE (catalog expectation).
 */
public record SkillGapItem(SkillDto skill, int currentLevel, SkillSource currentSource, boolean proven,
                           int requiredLevel, int gap, GapStatus status, int importance, String importanceSource,
                           Double demandShare, int jobsMentioning, double priority) {
}
