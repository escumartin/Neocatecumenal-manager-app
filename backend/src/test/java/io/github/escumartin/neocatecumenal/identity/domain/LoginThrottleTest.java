package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.IpState;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.Outcome;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.PairState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    private static Outcome.Reserved reserved(Outcome outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Reserved.class);
        return (Outcome.Reserved) outcome;
    }

    private static Outcome.Rejected rejected(Outcome outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Rejected.class);
        return (Outcome.Rejected) outcome;
    }

    private static PairState pairAfterFailures(int failures, Instant now) {
        PairState pair = PairState.empty();
        IpState ip = IpState.empty();
        for (int i = 0; i < failures; i++) {
            Outcome.Reserved result = reserved(LoginThrottle.reserve(ip, pair, now));
            pair = result.pair();
            ip = result.ip();
        }
        return pair;
    }

    @Test
    void firstAttemptIsCountedAsFailureBeforeVerification() {
        Outcome.Reserved result = reserved(LoginThrottle.reserve(IpState.empty(), PairState.empty(), T0));

        assertThat(result.pair().consecutiveFailures()).isEqualTo(1);
        assertThat(result.pair().lastFailureAt()).isEqualTo(T0);
        assertThat(result.pair().blockedUntil()).isNull();
        assertThat(result.ip().recentFailures()).containsExactly(T0);
        assertThat(result.ip().blockedUntil()).isNull();
    }

    @Test
    void fourthConsecutiveAttemptOfThePairDoesNotBlock() {
        PairState pair = pairAfterFailures(4, T0);

        assertThat(pair.consecutiveFailures()).isEqualTo(4);
        assertThat(pair.blockedUntil()).isNull();
    }

    @Test
    void fifthConsecutiveAttemptOfThePairBlocksForFifteenMinutes() {
        PairState pair = pairAfterFailures(5, T0);

        assertThat(pair.consecutiveFailures()).isEqualTo(5);
        assertThat(pair.blockedUntil()).isEqualTo(T0.plus(FIFTEEN_MINUTES));
    }

    @Test
    void sixthAttemptIsRejectedWithRetryAfterAndWithoutCounting() {
        PairState pair = pairAfterFailures(5, T0);
        Instant later = T0.plus(Duration.ofMinutes(5));

        Outcome.Rejected result = rejected(LoginThrottle.reserve(IpState.empty(), pair, later));

        assertThat(result.retryAfter()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void attemptExactlyAtBlockEndIsAllowedAndRestartsTheCount() {
        PairState pair = pairAfterFailures(5, T0);
        Instant blockEnd = T0.plus(FIFTEEN_MINUTES);

        Outcome.Reserved result = reserved(LoginThrottle.reserve(IpState.empty(), pair, blockEnd));

        assertThat(result.pair().consecutiveFailures()).isEqualTo(1);
        assertThat(result.pair().blockedUntil()).isNull();
    }

    @Test
    void pairCountRestartsAtOneWhenLastFailureIsFifteenMinutesOld() {
        PairState pair = pairAfterFailures(4, T0);

        Outcome.Reserved result = reserved(LoginThrottle.reserve(IpState.empty(), pair, T0.plus(FIFTEEN_MINUTES)));

        assertThat(result.pair().consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void pairCountContinuesWhenLastFailureIsYoungerThanFifteenMinutes() {
        PairState pair = pairAfterFailures(4, T0);

        Outcome.Reserved result = reserved(
                LoginThrottle.reserve(IpState.empty(), pair, T0.plus(FIFTEEN_MINUTES).minusSeconds(1)));

        assertThat(result.pair().consecutiveFailures()).isEqualTo(5);
    }

    @Test
    void nineteenthFailureFromAnIpDoesNotBlockIt() {
        IpState ip = IpState.empty();
        for (int i = 0; i < 19; i++) {
            ip = reserved(LoginThrottle.reserve(ip, PairState.empty(), T0.plusSeconds(i))).ip();
        }

        assertThat(ip.recentFailures()).hasSize(19);
        assertThat(ip.blockedUntil()).isNull();
    }

    @Test
    void twentiethFailureFromAnIpBlocksIt() {
        IpState ip = IpState.empty();
        Instant last = T0;
        for (int i = 0; i < 20; i++) {
            last = T0.plusSeconds(i);
            ip = reserved(LoginThrottle.reserve(ip, PairState.empty(), last)).ip();
        }

        assertThat(ip.recentFailures()).hasSize(20);
        assertThat(ip.blockedUntil()).isEqualTo(last.plus(FIFTEEN_MINUTES));
    }

    @Test
    void blockedIpRejectsEvenAPairWithNoFailures() {
        IpState blocked = new IpState(List.of(T0), T0.plus(FIFTEEN_MINUTES));

        Outcome.Rejected result = rejected(LoginThrottle.reserve(blocked, PairState.empty(), T0.plusSeconds(30)));

        assertThat(result.retryAfter()).isEqualTo(FIFTEEN_MINUTES.minusSeconds(30));
    }

    @Test
    void retryAfterIsTheLongestOfBothBlocks() {
        IpState ip = new IpState(List.of(T0), T0.plus(Duration.ofMinutes(10)));
        PairState pair = new PairState(5, T0, T0.plus(Duration.ofMinutes(14)));

        Outcome.Rejected result = rejected(LoginThrottle.reserve(ip, pair, T0));

        assertThat(result.retryAfter()).isEqualTo(Duration.ofMinutes(14));
    }

    @Test
    void failuresOutsideTheSlidingWindowAreDiscarded() {
        IpState ip = new IpState(List.of(T0, T0.plusSeconds(1), T0.plus(Duration.ofMinutes(10))), null);

        Outcome.Reserved result = reserved(LoginThrottle.reserve(ip, PairState.empty(), T0.plus(FIFTEEN_MINUTES)));

        assertThat(result.ip().recentFailures())
                .containsExactly(T0.plusSeconds(1), T0.plus(Duration.ofMinutes(10)), T0.plus(FIFTEEN_MINUTES));
    }

    @Test
    void failureExactlyFifteenMinutesOldLeavesTheWindow() {
        IpState ip = new IpState(List.of(T0), null);

        Outcome.Reserved result = reserved(LoginThrottle.reserve(ip, PairState.empty(), T0.plus(FIFTEEN_MINUTES)));

        assertThat(result.ip().recentFailures()).containsExactly(T0.plus(FIFTEEN_MINUTES));
    }

    @Test
    void ipBlockDoesNotApplyOnceItHasEnded() {
        IpState ip = new IpState(List.of(T0), T0.plus(FIFTEEN_MINUTES));

        Outcome outcome = LoginThrottle.reserve(ip, PairState.empty(), T0.plus(FIFTEEN_MINUTES));

        assertThat(outcome).isInstanceOf(Outcome.Reserved.class);
    }

    @Test
    void pairStateIsPurgeableOnceLastFailureAndBlockAreFifteenMinutesOld() {
        PairState pair = pairAfterFailures(3, T0);

        assertThat(LoginThrottle.isPurgeable(pair, T0.plus(FIFTEEN_MINUTES).minusSeconds(1))).isFalse();
        assertThat(LoginThrottle.isPurgeable(pair, T0.plus(FIFTEEN_MINUTES))).isTrue();
    }

    @Test
    void blockedPairStateIsPurgeableOnlyAfterItsBlockEnds() {
        PairState pair = new PairState(5, T0, T0.plus(Duration.ofMinutes(40)));

        assertThat(LoginThrottle.isPurgeable(pair, T0.plus(Duration.ofMinutes(39)))).isFalse();
        assertThat(LoginThrottle.isPurgeable(pair, T0.plus(Duration.ofMinutes(40)))).isTrue();
    }

    @Test
    void ipStateIsPurgeableAfterItsLastFailureLeavesTheWindowAndItsBlockEnds() {
        IpState ip = new IpState(List.of(T0, T0.plusSeconds(100)), T0.plus(Duration.ofMinutes(30)));

        assertThat(LoginThrottle.isPurgeable(ip, T0.plus(Duration.ofMinutes(29)))).isFalse();
        assertThat(LoginThrottle.isPurgeable(ip, T0.plus(Duration.ofMinutes(30)))).isTrue();
    }

    @Test
    void emptyStatesAreAlwaysPurgeable() {
        assertThat(LoginThrottle.isPurgeable(PairState.empty(), T0)).isTrue();
        assertThat(LoginThrottle.isPurgeable(IpState.empty(), T0)).isTrue();
    }

    @Test
    void successRestoresTheIpStateAndClearsThePair() {
        IpState before = new IpState(List.of(T0.minusSeconds(30)), null);
        Outcome.Reserved reservedAttempt = reserved(LoginThrottle.reserve(before, PairState.empty(), T0));

        LoginThrottle.Settled settled = LoginThrottle.onSuccess(before);

        assertThat(reservedAttempt.ip().recentFailures()).hasSize(2);
        assertThat(settled.ip()).isEqualTo(before);
        assertThat(settled.pair()).isEmpty();
    }
}
