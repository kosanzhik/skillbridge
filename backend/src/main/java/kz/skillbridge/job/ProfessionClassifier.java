package kz.skillbridge.job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import kz.skillbridge.career.Profession;
import kz.skillbridge.career.ProfessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assigns a vacancy to an MVP profession by phrases in its title (profession title, hh search terms and
 * title keywords). Vacancies that match no profession are not assigned — never guessed.
 */
@Component
@RequiredArgsConstructor
public class ProfessionClassifier {

    private final ProfessionRepository professions;

    public Optional<Profession> classify(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String t = title.toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (Profession p : professions.findAll()) {
            if (terms(p).stream().anyMatch(term ->
                    Pattern.compile("(?<![\\p{L}])" + Pattern.quote(term) + "(?![\\p{L}])").matcher(t).find())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    static List<String> terms(Profession p) {
        List<String> raw = new ArrayList<>();
        raw.add(p.getTitle());
        if (p.getHhQuery() != null) {
            raw.addAll(Arrays.asList(p.getHhQuery().split("(?i)\\s+OR\\s+")));
        }
        if (p.getTitleKeywords() != null) {
            raw.addAll(Arrays.asList(p.getTitleKeywords().split(",")));
        }
        return raw.stream().map(s -> s.strip().toLowerCase(Locale.ROOT).replace('ё', 'е'))
                .filter(s -> !s.isEmpty()).distinct().toList();
    }
}
