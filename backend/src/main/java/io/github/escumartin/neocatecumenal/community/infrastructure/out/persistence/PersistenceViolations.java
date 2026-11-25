package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class PersistenceViolations {

    static final String UNIQUE_VIOLATION = "23505";
    static final String FOREIGN_KEY_VIOLATION = "23503";

    private PersistenceViolations() {
    }

    static boolean isViolation(DataIntegrityViolationException failure, String sqlState, String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return sqlState.equals(violation.getSQLState())
                        && (constraint == null || constraint.equals(violation.getConstraintName()));
            }
        }
        return false;
    }
}
