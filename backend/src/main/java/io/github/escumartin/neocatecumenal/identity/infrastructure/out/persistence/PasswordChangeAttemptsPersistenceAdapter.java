package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordChangeAttempts;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PasswordChangeAttemptsPersistenceAdapter implements PasswordChangeAttempts {

    private final JdbcTemplate jdbc;

    PasswordChangeAttemptsPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int recordFailure(UserId userId) {
        Integer failures = jdbc.queryForObject("update identity_user "
                + "set failed_password_changes = failed_password_changes + 1 where id = ? "
                + "returning failed_password_changes", Integer.class, userId.value());
        return failures == null ? 0 : failures;
    }

    @Override
    @Transactional
    public void clear(UserId userId) {
        jdbc.update("update identity_user set failed_password_changes = 0 "
                + "where id = ? and failed_password_changes <> 0", userId.value());
    }
}
