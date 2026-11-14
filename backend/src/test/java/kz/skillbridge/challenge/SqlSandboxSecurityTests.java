package kz.skillbridge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kz.skillbridge.TestcontainersConfig;
import kz.skillbridge.challenge.grading.SqlSandbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SqlSandboxSecurityTests {

    @Autowired
    SqlSandbox sandbox;

    @Test
    void runsAsUnprivilegedRoleWithTrainingSchemaOnSearchPath() throws Exception {
        assertThat(sandbox.run("select current_user").rows().get(0).get(0)).isEqualTo("skillbridge_sandbox");
        assertThat(sandbox.run("select count(*) from customers").rows().get(0).get(0)).isEqualTo("8");
    }

    @Test
    void cannotReadApplicationTables() {
        assertThatThrownBy(() -> sandbox.run("select email, password_hash from public.users"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void cannotWriteOrRunOtherStatements() throws Exception {
        assertThatThrownBy(() -> sandbox.run("insert into sandbox.customers values (99,'x','y','2025-01-01')"))
                .hasMessageContaining("SELECT");
        assertThatThrownBy(() -> sandbox.run("select 1; drop table sandbox.customers"))
                .hasMessageContaining("один запрос");
        assertThatThrownBy(() -> sandbox.run("with x as (delete from sandbox.orders returning *) select * from x"))
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> sandbox.run("select set_config('role', 'test', false)"))
                .hasMessageContaining("permission denied");
        // semicolons and keywords inside literals are fine
        assertThat(sandbox.run("select 'a; drop table x' as s").rows()).hasSize(1);
    }

    @Test
    void longQueriesAreCancelled() {
        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> sandbox.run("select pg_sleep(30)")).hasMessageContaining("cancel");
        assertThat(System.currentTimeMillis() - start).isLessThan(10_000);
    }
}
