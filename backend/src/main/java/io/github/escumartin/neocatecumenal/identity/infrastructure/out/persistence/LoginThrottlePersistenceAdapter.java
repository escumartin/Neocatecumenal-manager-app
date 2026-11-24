package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.out.LoginThrottleStore;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.IpState;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.PairState;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginThrottlePersistenceAdapter implements LoginThrottleStore {

    private static final int MAX_LOCK_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;

    LoginThrottlePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IpState lockIp(String ip) {
        String address = normalise(ip);
        for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
            jdbc.update("insert into identity_login_ip_throttle (ip, recent_failures) "
                    + "values (cast(? as inet), array[]::timestamptz[]) on conflict (ip) do nothing", address);
            List<IpState> rows = jdbc.query("select recent_failures, blocked_until from identity_login_ip_throttle "
                    + "where ip = cast(? as inet) for update", (rs, row) -> ipState(rs), address);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        }
        throw new IllegalStateException("No se pudo bloquear la fila de intentos de la IP");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PairState lockPair(AccessId accessId, String ip) {
        String address = normalise(ip);
        for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
            jdbc.update("insert into identity_login_pair_throttle "
                    + "(access_id, ip, consecutive_failures, last_failure_at) "
                    + "values (?, cast(? as inet), 0, to_timestamp(0)) on conflict (access_id, ip) do nothing",
                    accessId.value(), address);
            List<PairState> rows = jdbc.query("select consecutive_failures, last_failure_at, blocked_until "
                    + "from identity_login_pair_throttle where access_id = ? and ip = cast(? as inet) "
                    + "for update", (rs, row) -> pairState(rs), accessId.value(), address);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        }
        throw new IllegalStateException("No se pudo bloquear la fila de intentos del par");
    }

    @Override
    @Transactional
    public void save(String ip, IpState state) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into identity_login_ip_throttle "
                    + "(ip, recent_failures, blocked_until) values (cast(? as inet), ?, ?) "
                    + "on conflict (ip) do update set recent_failures = excluded.recent_failures, "
                    + "blocked_until = excluded.blocked_until");
            Timestamp[] failures = state.recentFailures().stream().map(Timestamp::from).toArray(Timestamp[]::new);
            statement.setString(1, normalise(ip));
            statement.setArray(2, connection.createArrayOf("timestamptz", failures));
            setInstant(statement, 3, state.blockedUntil());
            return statement;
        });
    }

    @Override
    @Transactional
    public void save(AccessId accessId, String ip, PairState state) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into identity_login_pair_throttle "
                    + "(access_id, ip, consecutive_failures, last_failure_at, blocked_until) "
                    + "values (?, cast(? as inet), ?, ?, ?) on conflict (access_id, ip) do update set "
                    + "consecutive_failures = excluded.consecutive_failures, "
                    + "last_failure_at = excluded.last_failure_at, blocked_until = excluded.blocked_until");
            statement.setInt(1, accessId.value());
            statement.setString(2, normalise(ip));
            statement.setInt(3, state.consecutiveFailures());
            setInstant(statement, 4, state.lastFailureAt() == null ? Instant.EPOCH : state.lastFailureAt());
            setInstant(statement, 5, state.blockedUntil());
            return statement;
        });
    }

    @Override
    @Transactional
    public void deletePair(AccessId accessId, String ip) {
        jdbc.update("delete from identity_login_pair_throttle where access_id = ? and ip = cast(? as inet)",
                accessId.value(), normalise(ip));
    }

    @Override
    @Transactional
    public int purgeExpired(Instant now) {
        OffsetDateTime nowValue = now.atOffset(ZoneOffset.UTC);
        OffsetDateTime windowStart = now.minus(LoginThrottle.WINDOW).atOffset(ZoneOffset.UTC);
        int pairs = jdbc.update("delete from identity_login_pair_throttle "
                + "where last_failure_at <= ? and (blocked_until is null or blocked_until <= ?)",
                windowStart, nowValue);
        int ips = jdbc.update("delete from identity_login_ip_throttle "
                + "where (blocked_until is null or blocked_until <= ?) "
                + "and (cardinality(recent_failures) = 0 "
                + "or recent_failures[cardinality(recent_failures)] <= ?)", nowValue, windowStart);
        return pairs + ips;
    }

    private static void setInstant(java.sql.PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(index, value.atOffset(ZoneOffset.UTC));
        }
    }

    private static IpState ipState(ResultSet rs) throws SQLException {
        Array array = rs.getArray("recent_failures");
        List<Instant> failures = new ArrayList<>();
        for (Object element : (Object[]) array.getArray()) {
            failures.add(((Timestamp) element).toInstant());
        }
        return new IpState(failures, instant(rs, "blocked_until"));
    }

    private static PairState pairState(ResultSet rs) throws SQLException {
        int consecutive = rs.getInt("consecutive_failures");
        if (consecutive == 0) {
            return PairState.empty();
        }
        return new PairState(consecutive, instant(rs, "last_failure_at"), instant(rs, "blocked_until"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String normalise(String ip) {
        int zone = ip.indexOf('%');
        String address = zone < 0 ? ip : ip.substring(0, zone);
        return address.indexOf(':') < 0 ? address : sixtyFourBitPrefix(address);
    }

    private static String sixtyFourBitPrefix(String address) {
        try {
            InetAddress parsed = InetAddress.getByName(address);
            if (!(parsed instanceof Inet6Address)) {
                return parsed.getHostAddress();
            }
            byte[] prefix = new byte[16];
            System.arraycopy(parsed.getAddress(), 0, prefix, 0, 8);
            return InetAddress.getByAddress(prefix).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Dirección IP no válida", e);
        }
    }
}
