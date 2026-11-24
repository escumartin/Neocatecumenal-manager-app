package io.github.escumartin.neocatecumenal.identity.application;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserPage;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdAlreadyTakenException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IdentityEventPublisher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.LoginThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordChangeAttempts;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class Fakes {

    private Fakes() {
    }

    static final class InMemoryUsers implements UserRepository {

        final Map<UserId, User> users = new LinkedHashMap<>();

        final List<String> calls = new ArrayList<>();
        Integer administratorsSeenAfterLock;

        @Override
        public User save(User user) {
            boolean taken = users.values().stream()
                    .anyMatch(existing -> existing.accessId().equals(user.accessId())
                            && !existing.id().equals(user.id()));
            if (taken) {
                throw new AccessIdAlreadyTakenException();
            }
            users.put(user.id(), user);
            return user;
        }

        @Override
        public int lockAdministrators() {
            calls.add("lockAdministrators");
            if (administratorsSeenAfterLock != null) {
                return administratorsSeenAfterLock;
            }
            return (int) users.values().stream().filter(user -> user.role() == UserRole.ADMINISTRATOR).count();
        }

        @Override
        public void lockCommunity(CommunityId communityId) {
            calls.add("lockCommunity");
        }

        @Override
        public long countModerators(CommunityId communityId) {
            calls.add("countModerators");
            return users.values().stream()
                    .filter(user -> user.role() == UserRole.MODERATOR && user.communityId().equals(communityId))
                    .count();
        }

        @Override
        public UserPage findByCommunity(CommunityId communityId, int page, int size) {
            List<User> inCommunity = users.values().stream()
                    .filter(user -> user.communityId().equals(communityId))
                    .sorted(Comparator.comparing((User user) -> user.name().value()))
                    .toList();
            List<User> items = inCommunity.stream().skip((long) page * size).limit(size).toList();
            return new UserPage(items, page, size, inCommunity.size());
        }

        @Override
        public void delete(UserId id) {
            users.remove(id);
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByAccessId(AccessId accessId) {
            return users.values().stream().filter(user -> user.accessId().equals(accessId)).findFirst();
        }

        @Override
        public boolean existsAdministrator() {
            return users.values().stream().anyMatch(user -> user.role() == UserRole.ADMINISTRATOR);
        }
    }

    static final class InMemoryCommunities implements CommunityRepository {

        final Map<CommunityId, Community> communities = new LinkedHashMap<>();

        @Override
        public Community save(Community community) {
            communities.put(community.id(), community);
            return community;
        }

        @Override
        public Optional<Community> findById(CommunityId id) {
            return Optional.ofNullable(communities.get(id));
        }

        @Override
        public Optional<Community> findByNumber(CommunityNumber number) {
            return communities.values().stream().filter(community -> community.number().equals(number)).findFirst();
        }

        @Override
        public List<Community> findAll() {
            return communities.values().stream()
                    .sorted(Comparator.comparingInt(community -> community.number().value()))
                    .toList();
        }
    }

    static final class InMemorySessions implements SessionRepository {

        final Map<String, Session> sessions = new LinkedHashMap<>();

        private static String key(byte[] hash) {
            return Base64.getEncoder().encodeToString(hash);
        }

        @Override
        public Session save(Session session) {
            sessions.put(key(session.tokenHash()), session);
            return session;
        }

        @Override
        public Optional<Session> findByTokenHash(byte[] tokenHash) {
            return Optional.ofNullable(sessions.get(key(tokenHash)));
        }

        @Override
        public void delete(UUID sessionId) {
            sessions.values().removeIf(session -> session.id().equals(sessionId));
        }

        @Override
        public void deleteAllOf(UserId userId) {
            sessions.values().removeIf(session -> session.userId().equals(userId));
        }

        @Override
        public void deleteAllOfExcept(UserId userId, UUID sessionId) {
            sessions.values().removeIf(session -> session.userId().equals(userId) && !session.id().equals(sessionId));
        }

        @Override
        public void retainMostRecentlyUsed(UserId userId, int maxSessions) {
            List<Session> surplus = sessions.values().stream()
                    .filter(session -> session.userId().equals(userId))
                    .sorted(Comparator.comparing(Session::lastUsedAt).reversed()
                            .thenComparing(Comparator.comparing(Session::createdAt).reversed())
                            .thenComparing(Session::id))
                    .skip(maxSessions)
                    .toList();
            sessions.values().removeAll(surplus);
        }

        @Override
        public int purgeExpired(Instant now) {
            List<Session> expired = sessions.values().stream().filter(session -> !session.isValid(now)).toList();
            sessions.values().removeAll(expired);
            return expired.size();
        }
    }

    static final class InMemoryPasswordChangeAttempts implements PasswordChangeAttempts {

        private final Map<UserId, Integer> failures = new HashMap<>();

        @Override
        public int recordFailure(UserId userId) {
            return failures.merge(userId, 1, Integer::sum);
        }

        @Override
        public void clear(UserId userId) {
            failures.remove(userId);
        }
    }

    static final class RecordingEvents implements IdentityEventPublisher {

        final List<UserDeleted> published = new ArrayList<>();

        @Override
        public void publish(UserDeleted event) {
            published.add(event);
        }
    }

    static final class FakeHasher implements PasswordHasher {

        int dummyVerifications;
        int verifications;
        Runnable beforeVerification = () -> { };

        @Override
        public PasswordHash hash(PlainPassword password) {
            return new PasswordHash("{fake}" + password.value());
        }

        @Override
        public boolean matches(PlainPassword password, PasswordHash hash) {
            verifications++;
            beforeVerification.run();
            return hash.value().equals("{fake}" + password.value());
        }

        @Override
        public boolean matchesDummy(PlainPassword password) {
            dummyVerifications++;
            beforeVerification.run();
            return false;
        }
    }

    static final class FakeTokens implements SessionTokenIssuer {

        private int counter;

        @Override
        public IssuedSessionToken issue() {
            String token = "token-" + (++counter);
            return new IssuedSessionToken(token, hash(token));
        }

        @Override
        public byte[] hash(String token) {
            return ("hash-" + token).getBytes(StandardCharsets.UTF_8);
        }
    }

    static final class SequenceAccessIds implements AccessIdGenerator {

        private final java.util.ArrayDeque<Integer> values = new java.util.ArrayDeque<>();

        SequenceAccessIds(Integer... values) {
            this.values.addAll(List.of(values));
        }

        @Override
        public AccessId next() {
            return new AccessId(values.removeFirst());
        }
    }

    static final class FixedInitialPassword implements InitialPasswordGenerator {

        @Override
        public PlainPassword next() {
            return new PlainPassword("initialpass");
        }
    }

    static final class FakeSessionContext implements CurrentSessionContext {

        CurrentSession session;

        @Override
        public Optional<CurrentSession> current() {
            return Optional.ofNullable(session);
        }
    }

    static final class FixedPrivacyNotice implements PrivacyNoticeSettings {

        @Override
        public String currentVersion() {
            return "v2";
        }
    }

    static final class InMemoryThrottleStore implements LoginThrottleStore {

        final Map<String, LoginThrottle.IpState> ips = new HashMap<>();
        final Map<String, LoginThrottle.PairState> pairs = new HashMap<>();
        final List<String> lockOrder = new ArrayList<>();

        private static String pairKey(AccessId accessId, String ip) {
            return accessId.value() + "@" + ip;
        }

        @Override
        public LoginThrottle.IpState lockIp(String ip) {
            lockOrder.add("ip");
            return ips.computeIfAbsent(ip, key -> LoginThrottle.IpState.empty());
        }

        @Override
        public LoginThrottle.PairState lockPair(AccessId accessId, String ip) {
            lockOrder.add("pair");
            return pairs.computeIfAbsent(pairKey(accessId, ip), key -> LoginThrottle.PairState.empty());
        }

        @Override
        public void save(String ip, LoginThrottle.IpState state) {
            ips.put(ip, state);
        }

        @Override
        public void save(AccessId accessId, String ip, LoginThrottle.PairState state) {
            pairs.put(pairKey(accessId, ip), state);
        }

        @Override
        public void deletePair(AccessId accessId, String ip) {
            pairs.remove(pairKey(accessId, ip));
        }

        @Override
        public int purgeExpired(Instant now) {
            int before = ips.size() + pairs.size();
            ips.values().removeIf(state -> LoginThrottle.isPurgeable(state, now));
            pairs.values().removeIf(state -> LoginThrottle.isPurgeable(state, now));
            return before - ips.size() - pairs.size();
        }

        LoginThrottle.PairState pair(AccessId accessId, String ip) {
            return pairs.getOrDefault(pairKey(accessId, ip), LoginThrottle.PairState.empty());
        }
    }
}
