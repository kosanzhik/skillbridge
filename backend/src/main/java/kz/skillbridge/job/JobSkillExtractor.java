package kz.skillbridge.job;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import kz.skillbridge.skill.Skill;
import kz.skillbridge.skill.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Deterministic requirement extraction (spec §10 "Job Processor"): explicit skill tags count as REQUIRED;
 * catalog skills mentioned in the text are REQUIRED unless they only appear under a
 * "nice to have / будет плюсом" heading, in which case they are PREFERRED.
 */
@Component
@RequiredArgsConstructor
public class JobSkillExtractor {

    private static final Pattern PREFERRED_HEADING = Pattern.compile(
            "(?iu)(будет плюсом|будет преимуществом|преимуществом будет|желательно|приветствуется|"
                    + "nice to have|nice-to-have|preferred|a plus|bonus|плюсом будет)");
    private static final Pattern REQUIRED_HEADING = Pattern.compile(
            "(?iu)(требования|обязательно|мы ожидаем|ожидаем от|что нужно|необходимо|requirements|"
                    + "must have|you have|обязанности|responsibilities|задачи)");
    private static final int MAX_HEADING_LEN = 80;

    private final SkillNormalizer normalizer;

    public Map<Skill, RequirementType> extract(Collection<String> explicitSkills, String text) {
        Map<Skill, RequirementType> out = new LinkedHashMap<>();
        if (explicitSkills != null) {
            for (String name : explicitSkills) {
                normalizer.match(name).ifPresent(s -> out.put(s, RequirementType.REQUIRED));
            }
        }
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder required = new StringBuilder();
        StringBuilder preferred = new StringBuilder();
        boolean inPreferred = false;
        for (String line : text.split("\\n")) {
            String l = line.strip();
            if (l.length() <= MAX_HEADING_LEN && PREFERRED_HEADING.matcher(l).find()) {
                inPreferred = true;
                // "Желательно: Python, Tableau" — the rest of the line belongs to the section
                int colon = l.indexOf(':');
                if (colon >= 0) {
                    preferred.append(l.substring(colon + 1)).append('\n');
                }
                continue;
            }
            if (l.length() <= MAX_HEADING_LEN && REQUIRED_HEADING.matcher(l).find()) {
                inPreferred = false;
            }
            (inPreferred ? preferred : required).append(l).append('\n');
        }
        normalizer.findMentions(required.toString()).keySet()
                .forEach(s -> out.putIfAbsent(s, RequirementType.REQUIRED));
        normalizer.findMentions(preferred.toString()).keySet()
                .forEach(s -> out.putIfAbsent(s, RequirementType.PREFERRED));
        return out;
    }
}
