package io.github.escumartin.neocatecumenal.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LoginThrottle {

    public static final int PAIR_MAX_CONSECUTIVE = 5;
    public static final int IP_MAX_IN_WINDOW = 20;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration BLOCK = Duration.ofMinutes(15);

    private LoginThrottle() {
    }

    public record PairState(int consecutiveFailures, Instant lastFailureAt, Instant blockedUntil) {

        public static PairState empty() {
            return new PairState(0, null, null);
        }
    }

    public record IpState(List<Instant> recentFailures, Instant blockedUntil) {

        public IpState {
            recentFailures = List.copyOf(recentFailures);
        }

        public static IpState empty() {
            return new IpState(List.of(), null);
        }
    }

    public sealed interface Outcome {

        record Reserved(IpState ip, PairState pair) implements Outcome {
        }

        record Rejected(Duration retryAfter) implements Outcome {
        }
    }

    public record Settled(IpState ip, Optional<PairState> pair) {
    }

    public static Settled onSuccess(IpState ipBefore) {
        return new Settled(ipBefore, Optional.empty());
    }

    public static Outcome reserve(IpState ip, PairState pair, Instant now) {
        Duration retryAfter = longest(remaining(ip.blockedUntil(), now), remaining(pair.blockedUntil(), now));
        if (!retryAfter.isZero()) {
            return new Outcome.Rejected(retryAfter);
        }
        return new Outcome.Reserved(reserveIp(ip, now), reservePair(pair, now));
    }

    public static boolean isPurgeable(PairState pair, Instant now) {
        return !now.isBefore(retainedUntil(pair.lastFailureAt(), pair.blockedUntil()));
    }

    public static boolean isPurgeable(IpState ip, Instant now) {
        Instant lastFailure = ip.recentFailures().isEmpty() ? null : ip.recentFailures().getLast();
        return !now.isBefore(retainedUntil(lastFailure, ip.blockedUntil()));
    }

    private static PairState reservePair(PairState pair, Instant now) {
        boolean continues = pair.lastFailureAt() != null && now.isBefore(pair.lastFailureAt().plus(WINDOW));
        int failures = continues ? pair.consecutiveFailures() + 1 : 1;
        Instant blockedUntil = failures >= PAIR_MAX_CONSECUTIVE ? now.plus(BLOCK) : null;
        return new PairState(failures, now, blockedUntil);
    }

    private static IpState reserveIp(IpState ip, Instant now) {
        Instant windowStart = now.minus(WINDOW);
        List<Instant> failures = new ArrayList<>(ip.recentFailures().stream()
                .filter(failure -> failure.isAfter(windowStart))
                .toList());
        failures.add(now);
        Instant blockedUntil = failures.size() >= IP_MAX_IN_WINDOW ? now.plus(BLOCK) : null;
        return new IpState(failures, blockedUntil);
    }

    private static Duration remaining(Instant blockedUntil, Instant now) {
        if (blockedUntil == null || !blockedUntil.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, blockedUntil);
    }

    private static Duration longest(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static Instant retainedUntil(Instant lastFailureAt, Instant blockedUntil) {
        Instant fromFailure = lastFailureAt == null ? Instant.MIN : lastFailureAt.plus(WINDOW);
        Instant fromBlock = blockedUntil == null ? Instant.MIN : blockedUntil;
        return fromFailure.isAfter(fromBlock) ? fromFailure : fromBlock;
    }
}
