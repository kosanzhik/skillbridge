package kz.skillbridge.skill;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single source of truth for "what level does the user have in a skill". */
@Service
@RequiredArgsConstructor
public class SkillProfileService {

    public static final Set<SkillSource> PROVEN = EnumSet.of(SkillSource.ASSESSED, SkillSource.VERIFIED);

    private final UserSkillRepository userSkills;

    public record Level(int level, SkillSource source) {
        public boolean proven() {
            return PROVEN.contains(source);
        }
    }

    /** Proven results (ASSESSED/VERIFIED) override self/CV claims, even when lower. */
    @Transactional(readOnly = true)
    public Map<Long, Level> effectiveLevels(Long userId) {
        Map<Long, Level> claimed = new HashMap<>();
        Map<Long, Level> proven = new HashMap<>();
        for (UserSkill us : userSkills.findByUserIdOrderBySkillName(userId)) {
            Map<Long, Level> target = PROVEN.contains(us.getSource()) ? proven : claimed;
            target.merge(us.getSkill().getId(), new Level(us.getLevel(), us.getSource()),
                    (a, b) -> a.level() >= b.level() ? a : b);
        }
        claimed.putAll(proven);
        return claimed;
    }
}
