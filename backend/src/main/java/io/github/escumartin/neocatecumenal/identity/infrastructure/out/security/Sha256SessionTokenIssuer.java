package io.github.escumartin.neocatecumenal.identity.infrastructure.out.security;

import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class Sha256SessionTokenIssuer implements SessionTokenIssuer {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random;

    public Sha256SessionTokenIssuer() {
        this(new SecureRandom());
    }

    public Sha256SessionTokenIssuer(SecureRandom random) {
        this.random = random;
    }

    @Override
    public IssuedSessionToken issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedSessionToken(token, hash(token));
    }

    @Override
    public byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
