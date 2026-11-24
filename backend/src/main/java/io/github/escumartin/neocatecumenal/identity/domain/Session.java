package io.github.escumartin.neocatecumenal.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Session(UUID id, UserId userId, byte[] tokenHash, Instant createdAt, Instant lastUsedAt) {

    public static final Duration IDLE_TIMEOUT = Duration.ofDays(30);
    public static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(90);
    public static final int MAX_PER_USER = 10;
    public static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    public static Session open(UserId userId, byte[] tokenHash, Instant now) {
        return new Session(UUID.randomUUID(), userId, tokenHash, now, now);
    }

    public boolean isValid(Instant now) {
        return now.isBefore(lastUsedAt.plus(IDLE_TIMEOUT)) && now.isBefore(createdAt.plus(ABSOLUTE_TIMEOUT));
    }

    public boolean needsTouch(Instant now) {
        return !now.isBefore(lastUsedAt.plus(TOUCH_INTERVAL));
    }

    public Session touch(Instant now) {
        if (!needsTouch(now)) {
            return this;
        }
        return new Session(id, userId, tokenHash, createdAt, now);
    }
}
