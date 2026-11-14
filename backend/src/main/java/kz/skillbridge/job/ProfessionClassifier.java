package kz.skillbridge.job;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import kz.skillbridge.career.Profession;
import kz.skillbridge.career.ProfessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Assigns a vacancy title to an MVP profession using the profession's title and search terms. */
@Component
@RequiredArgsConstructor
public class ProfessionClassifier {

    private final ProfessionRepository professions;

    public Optional<Profession> classify(String title) {
        if (title == null) {
            return Optional.empty();
        }
        String t = title.toLowerCase(Locale.ROOT);
        for (Profession p : professions.findAll()) {
            String terms = p.getTitle() + (p.getHhQuery() == null ? "" : " OR " + p.getHhQuery());
            boolean hit = Arrays.stream(terms.split("(?i)\\s+OR\\s+"))
                    .map(s -> s.strip().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .anyMatch(term -> Pattern.compile("(?<![\\p{L}])" + Pattern.quote(term) + "(?![\\p{L}])")
                            .matcher(t).find());
            if (hit) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
