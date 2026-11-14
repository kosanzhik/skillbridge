package kz.skillbridge.challenge.grading;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

/** Runs SQL as the unprivileged sandbox role in a read-only transaction with timeouts and a row cap. */
@Component
public class SqlSandbox {

    static final int MAX_ROWS = 500;
    static final int TIMEOUT_SECONDS = 5;

    private final HikariDataSource ds;

    public record Result(List<String> columns, List<List<String>> rows, boolean truncated) {
    }

    public static class SandboxException extends Exception {
        public SandboxException(String message) {
            super(message);
        }
    }

    public SqlSandbox(JdbcConnectionDetails db, SandboxProperties props) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("sql-sandbox");
        cfg.setJdbcUrl(db.getJdbcUrl());
        cfg.setUsername(props.username());
        cfg.setPassword(props.password());
        cfg.setMaximumPoolSize(3);
        cfg.setMinimumIdle(0);
        cfg.setReadOnly(true);
        cfg.setAutoCommit(false);
        cfg.setInitializationFailTimeout(-1); // app must start even if the sandbox role is unavailable
        cfg.addDataSourceProperty("options", "-c statement_timeout=3000 -c search_path=sandbox");
        this.ds = new HikariDataSource(cfg);
    }

    public Result run(String sql) throws SandboxException {
        String query;
        try {
            query = SqlGuard.check(sql);
        } catch (IllegalArgumentException e) {
            throw new SandboxException(e.getMessage());
        }
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(query)) {
                ps.setQueryTimeout(TIMEOUT_SECONDS);
                ps.setMaxRows(MAX_ROWS + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        columns.add(md.getColumnLabel(i));
                    }
                    List<List<String>> rows = new ArrayList<>();
                    while (rs.next() && rows.size() <= MAX_ROWS) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= columns.size(); i++) {
                            row.add(normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    boolean truncated = rows.size() > MAX_ROWS;
                    return new Result(columns, truncated ? rows.subList(0, MAX_ROWS) : rows, truncated);
                }
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "SQL error" : e.getMessage().split("\\R")[0];
            throw new SandboxException(msg);
        }
    }

    /** Numbers compared with 2-decimal precision so 1525000 equals 1525000.00. */
    static String normalize(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof Number n) {
            BigDecimal d = new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
            return d.toPlainString();
        }
        return v.toString().strip();
    }

    @PreDestroy
    void close() {
        ds.close();
    }
}
