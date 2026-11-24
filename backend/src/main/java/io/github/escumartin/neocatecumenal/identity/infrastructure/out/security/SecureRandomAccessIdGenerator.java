package io.github.escumartin.neocatecumenal.identity.infrastructure.out.security;

import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomAccessIdGenerator implements AccessIdGenerator {

    private static final int RANGE = AccessId.MAX - AccessId.MIN + 1;

    private final SecureRandom random;

    public SecureRandomAccessIdGenerator() {
        this(new SecureRandom());
    }

    public SecureRandomAccessIdGenerator(SecureRandom random) {
        this.random = random;
    }

    @Override
    public AccessId next() {
        return new AccessId(AccessId.MIN + random.nextInt(RANGE));
    }
}
