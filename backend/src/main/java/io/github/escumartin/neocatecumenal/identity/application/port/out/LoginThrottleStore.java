package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle;
import java.time.Instant;

public interface LoginThrottleStore {

    LoginThrottle.IpState lockIp(String ip);

    LoginThrottle.PairState lockPair(AccessId accessId, String ip);

    void save(String ip, LoginThrottle.IpState state);

    void save(AccessId accessId, String ip, LoginThrottle.PairState state);

    void deletePair(AccessId accessId, String ip);

    int purgeExpired(Instant now);
}
