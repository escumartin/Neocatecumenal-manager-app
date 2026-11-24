package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");
    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final byte[] HASH = new byte[] {1, 2, 3};

    private static Session sessionUsedAt(Instant lastUsedAt) {
        return new Session(UUID.randomUUID(), USER, HASH, START, lastUsedAt);
    }

    @Test
    void openStartsAtNowWithFreshId() {
        Session first = Session.open(USER, HASH, START);
        Session second = Session.open(USER, HASH, START);

        assertThat(first.createdAt()).isEqualTo(START);
        assertThat(first.lastUsedAt()).isEqualTo(START);
        assertThat(first.userId()).isEqualTo(USER);
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void isValidJustBeforeThirtyDaysWithoutUse() {
        Session session = Session.open(USER, HASH, START);

        assertThat(session.isValid(START.plus(Duration.ofDays(30)).minusSeconds(1))).isTrue();
    }

    @Test
    void isInvalidAtThirtyDaysWithoutUse() {
        Session session = Session.open(USER, HASH, START);

        assertThat(session.isValid(START.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test
    void recentUseKeepsSessionValidBeyondThirtyDaysFromCreation() {
        Session session = sessionUsedAt(START.plus(Duration.ofDays(60)));

        assertThat(session.isValid(START.plus(Duration.ofDays(70)))).isTrue();
    }

    @Test
    void isValidJustBeforeNinetyDaysAbsolute() {
        Instant now = START.plus(Duration.ofDays(90)).minusSeconds(1);
        Session session = sessionUsedAt(now.minusSeconds(60));

        assertThat(session.isValid(now)).isTrue();
    }

    @Test
    void isInvalidAtNinetyDaysAbsoluteEvenWhenUsedJustNow() {
        Instant now = START.plus(Duration.ofDays(90));
        Session session = sessionUsedAt(now.minusSeconds(60));

        assertThat(session.isValid(now)).isFalse();
    }

    @Test
    void touchBeforeFiveMinutesKeepsLastUsedAt() {
        Session session = Session.open(USER, HASH, START);

        Session touched = session.touch(START.plus(Duration.ofMinutes(5)).minusSeconds(1));

        assertThat(touched.lastUsedAt()).isEqualTo(START);
    }

    @Test
    void touchAtFiveMinutesRenewsLastUsedAt() {
        Session session = Session.open(USER, HASH, START);
        Instant now = START.plus(Duration.ofMinutes(5));

        Session touched = session.touch(now);

        assertThat(touched.lastUsedAt()).isEqualTo(now);
        assertThat(touched.id()).isEqualTo(session.id());
        assertThat(touched.createdAt()).isEqualTo(START);
        assertThat(touched.tokenHash()).isEqualTo(HASH);
    }

    @Test
    void touchReportsWhetherItRenewed() {
        Session session = Session.open(USER, HASH, START);

        assertThat(session.needsTouch(START.plus(Duration.ofMinutes(4)))).isFalse();
        assertThat(session.needsTouch(START.plus(Duration.ofMinutes(5)))).isTrue();
    }
}
