package kz.skillbridge.resume;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kz.skillbridge.resume.dto.ParsedResume;
import kz.skillbridge.resume.dto.ParsedResume.Certification;
import kz.skillbridge.resume.dto.ParsedResume.Education;
import kz.skillbridge.resume.dto.ParsedResume.Experience;
import kz.skillbridge.resume.dto.ParsedResume.ParsedSkill;
import kz.skillbridge.resume.dto.ParsedResume.Project;

/** Validates and normalizes extractor / user output before it is shown or persisted (spec §10). */
final class ResumeSanitizer {

    static final int DEFAULT_CV_LEVEL = 2;
    private static final Pattern DATE = Pattern.compile("^(\\d{4})(?:[-./](\\d{1,2}))?(?:[-./]\\d{1,2})?$");

    private ResumeSanitizer() {
    }

    static ParsedResume clean(ParsedResume r) {
        if (r == null) {
            return new ParsedResume(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new ParsedResume(
                str(r.fullName(), 255),
                str(r.headline(), 255),
                str(r.location(), 255),
                list(r.languages(), 10, s -> str(s, 40)).stream().distinct().toList(),
                list(r.experiences(), 30, ResumeSanitizer::experience),
                list(r.educations(), 15, ResumeSanitizer::education),
                list(r.projects(), 30, ResumeSanitizer::project),
                list(r.certifications(), 30, ResumeSanitizer::certification),
                skills(r.skills()));
    }

    private static Experience experience(Experience e) {
        String title = str(e.title(), 255);
        if (title == null) {
            return null;
        }
        return new Experience(str(e.company(), 255), title, yearMonth(e.startDate()), yearMonth(e.endDate()),
                str(e.description(), 4000));
    }

    private static Education education(Education e) {
        String institution = str(e.institution(), 255);
        if (institution == null) {
            return null;
        }
        return new Education(institution, str(e.degree(), 255), str(e.field(), 255), year(e.startYear()),
                year(e.endYear()));
    }

    private static Project project(Project p) {
        String name = str(p.name(), 255);
        if (name == null) {
            return null;
        }
        return new Project(name, str(p.description(), 2000), list(p.skills(), 20, s -> str(s, 100)));
    }

    private static Certification certification(Certification c) {
        String name = str(c.name(), 255);
        return name == null ? null : new Certification(name, str(c.issuer(), 255), year(c.year()));
    }

    /** Dedupes by name (case-insensitive), keeping the highest level; clamps level to 1..5. */
    private static List<ParsedSkill> skills(List<ParsedSkill> in) {
        Map<String, ParsedSkill> byName = new LinkedHashMap<>();
        for (ParsedSkill s : list(in, 100, Function.identity())) {
            String name = str(s.name(), 100);
            if (name == null) {
                continue;
            }
            int level = s.level() == null ? DEFAULT_CV_LEVEL : Math.max(1, Math.min(5, s.level()));
            ParsedSkill cleaned = new ParsedSkill(name, level, str(s.evidence(), 300), s.skillId(), null);
            byName.merge(name.toLowerCase(Locale.ROOT), cleaned, (a, b) -> a.level() >= b.level() ? a : b);
        }
        return new ArrayList<>(byName.values());
    }

    static String yearMonth(String s) {
        String v = str(s, 20);
        if (v == null) {
            return null;
        }
        Matcher m = DATE.matcher(v);
        if (!m.matches()) {
            return null;
        }
        int year = Integer.parseInt(m.group(1));
        int month = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
        if (year < 1950 || year > 2100 || month < 1 || month > 12) {
            return null;
        }
        return "%04d-%02d".formatted(year, month);
    }

    private static Integer year(Integer y) {
        return y == null || y < 1950 || y > 2100 ? null : y;
    }

    private static String str(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static <T, R> List<R> list(List<T> in, int max, Function<T, R> mapper) {
        if (in == null) {
            return List.of();
        }
        return in.stream().filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).limit(max).toList();
    }
}
