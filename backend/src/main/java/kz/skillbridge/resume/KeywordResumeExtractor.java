package kz.skillbridge.resume;

import java.util.List;
import kz.skillbridge.resume.dto.ParsedResume;
import kz.skillbridge.resume.dto.ParsedResume.ParsedSkill;
import kz.skillbridge.skill.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fallback without AI: only finds catalog skills mentioned in the text. It does not guess experience
 * or education — the user fills those in during review. Level is the conservative CV default.
 */
@Component
@RequiredArgsConstructor
class KeywordResumeExtractor implements ResumeExtractor {

    private final SkillNormalizer normalizer;

    @Override
    public ParserType type() {
        return ParserType.KEYWORD;
    }

    @Override
    public ParsedResume extract(String cvText) {
        List<ParsedSkill> skills = normalizer.findMentions(cvText).entrySet().stream()
                .map(e -> new ParsedSkill(e.getKey().getName(), ResumeSanitizer.DEFAULT_CV_LEVEL,
                        "Упомянуто в CV: «" + e.getValue() + "»", e.getKey().getId(), e.getKey().getName()))
                .toList();
        return new ParsedResume(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), skills);
    }
}
