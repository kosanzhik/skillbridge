package kz.skillbridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SkillbridgeApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migrationsApplyAndSeedCatalog() {
		assertThat(jdbc.queryForObject("select count(*) from professions", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from skills", Integer.class)).isGreaterThan(30);
		// every MVP profession has baseline skill expectations
		assertThat(jdbc.queryForObject(
				"select count(*) from professions p where not exists "
						+ "(select 1 from profession_skills ps where ps.profession_id = p.id)", Integer.class))
				.isZero();
	}
}
