package kz.skillbridge.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps free-text skill names to the skill catalog via slug, name and aliases (spec §10 "Skill Normalizer").
 * The catalog is small, so it is read on each call; switch to a cache if it grows.
 */
@Component
@RequiredArgsConstructor
public class SkillNormalizer {

    /** Too short/ambiguous to search for inside free text (e.g. the letter "R"). */
    private static final Set<String> TEXT_SCAN_STOPWORDS = Set.of("r", "ts", "js", "ml", "rest", "api");

    private final SkillRepository skills;

    public Optional<Skill> match(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index(true).get(normalize(rawName)));
    }

    /** Finds catalog skills mentioned in text, keyed by skill id, with the term that matched. */
    public Map<Skill, String> findMentions(String text) {
        Map<Skill, String> found = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        String haystack = normalize(text);
        // slugs are identifiers, not natural phrases: "requirements"/"testing" would match ordinary headings
        for (Map.Entry<String, Skill> e : index(false).entrySet()) {
            String term = e.getKey();
            if (term.length() < 2 || TEXT_SCAN_STOPWORDS.contains(term) || found.containsKey(e.getValue())) {
                continue;
            }
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])");
            if (p.matcher(haystack).find()) {
                found.put(e.getValue(), term);
            }
        }
        return found;
    }

    private Map<String, Skill> index(boolean includeSlugs) {
        Map<String, Skill> idx = new HashMap<>();
        for (Skill s : skills.findAll()) {
            List<String> terms = new ArrayList<>(includeSlugs ? List.of(s.getSlug(), s.getName()) : List.of(s.getName()));
            terms.addAll(s.aliasList());
            for (String t : terms) {
                idx.putIfAbsent(normalize(t), s);
            }
        }
        return idx;
    }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .trim();
    }
}
