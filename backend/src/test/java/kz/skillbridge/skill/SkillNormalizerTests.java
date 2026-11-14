package kz.skillbridge.skill;

import static org.assertj.core.api.Assertions.assertThat;

import kz.skillbridge.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SkillNormalizerTests {

    @Autowired
    SkillNormalizer normalizer;

    private java.util.List<String> slugs(String text) {
        return normalizer.findMentions(text).keySet().stream().map(Skill::getSlug).toList();
    }

    @Test
    void slugsAreNotMatchedInFreeText() {
        // "requirements" and "testing" are slugs of Requirements Engineering / Software Testing
        assertThat(slugs("Requirements:\n- SQL and Excel\n- experience with A/B testing"))
                .containsExactlyInAnyOrder("sql", "excel", "statistics") // "a/b testing" is a Statistics alias
                .doesNotContain("requirements", "testing");
    }

    @Test
    void namesAndAliasesStillMatch() {
        assertThat(slugs("Write user stories, Power BI dashboards, unit tests; финансовое моделирование"))
                .contains("requirements", "power-bi", "testing", "financial-modeling");
        assertThat(normalizer.match("requirements")).map(Skill::getSlug).contains("requirements"); // exact match keeps slugs
    }
}
