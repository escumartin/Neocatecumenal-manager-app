package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class FlywayTestSupport {

    private FlywayTestSupport() {
    }

    public static JdbcTemplate migratedTemplate(PostgreSQLContainer container) {
        DataSource dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(),
                container.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }

    public static void assertSqlState(ThrowingCallable action, String sqlState) {
        try {
            action.call();
        } catch (Throwable thrown) {
            Throwable cause = thrown;
            while (cause.getCause() != null && !(cause instanceof SQLException)) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(sqlState);
            return;
        }
        throw new AssertionError("Expected SQL state " + sqlState + " but nothing was thrown");
    }
}
