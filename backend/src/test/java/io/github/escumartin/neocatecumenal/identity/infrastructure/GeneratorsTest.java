package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordPolicy;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.security.ClasspathCommonPasswords;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.security.RandomInitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.security.SecureRandomAccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.security.Sha256SessionTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneratorsTest {

    private static final String CONFUSING = "01ol";

    private static final class FixedRandom extends SecureRandom {
        private final int fixed;

        FixedRandom(int fixed) {
            this.fixed = fixed;
        }

        @Override
        public int nextInt(int bound) {
            return fixed < 0 ? bound - 1 : fixed;
        }
    }

    @Test
    void accessIdStaysWithinSixDigits() {
        SecureRandomAccessIdGenerator generator = new SecureRandomAccessIdGenerator(new SecureRandom());

        for (int i = 0; i < 2000; i++) {
            assertThat(generator.next().value()).isBetween(100000, 999999);
        }
    }

    @Test
    void accessIdReachesBothBounds() {
        assertThat(new SecureRandomAccessIdGenerator(new FixedRandom(0)).next()).isEqualTo(new AccessId(100000));
        assertThat(new SecureRandomAccessIdGenerator(new FixedRandom(-1)).next()).isEqualTo(new AccessId(999999));
    }

    @Test
    void accessIdVaries() {
        SecureRandomAccessIdGenerator generator = new SecureRandomAccessIdGenerator(new SecureRandom());
        Set<Integer> values = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            values.add(generator.next().value());
        }

        assertThat(values.size()).isGreaterThan(40);
    }

    @Test
    void initialPasswordHasTenCharactersWithoutConfusingOnes() {
        RandomInitialPasswordGenerator generator = new RandomInitialPasswordGenerator(new SecureRandom());

        for (int i = 0; i < 500; i++) {
            String password = generator.next().value();

            assertThat(password).hasSize(10).matches("[a-z2-9]{10}");
            assertThat(password).doesNotContainAnyWhitespaces();
            for (char confusing : CONFUSING.toCharArray()) {
                assertThat(password).doesNotContain(String.valueOf(confusing));
            }
        }
    }

    @Test
    void initialPasswordNeverContainsSixConsecutiveDigits() {
        RandomInitialPasswordGenerator generator = new RandomInitialPasswordGenerator(new SecureRandom());

        for (int i = 0; i < 20000; i++) {
            assertThat(generator.next().value()).doesNotContainPattern("\\d{6}");
        }
    }

    @Test
    void initialPasswordBreaksDigitRunsWhenRandomnessOnlyPicksDigits() {
        SecureRandom onlyDigits = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return bound == 32 ? 24 : 0;
            }
        };

        String password = new RandomInitialPasswordGenerator(onlyDigits).next().value();

        assertThat(password).hasSize(10).doesNotContainPattern("\\d{6}");
    }

    @Test
    void initialPasswordFitsPasswordLengthRange() {
        PlainPassword password = new RandomInitialPasswordGenerator(new SecureRandom()).next();

        assertThat(password.value().codePointCount(0, password.value().length())).isBetween(8, 64);
    }

    @Test
    void initialPasswordAlwaysSatisfiesThePasswordPolicy() {
        RandomInitialPasswordGenerator generator = new RandomInitialPasswordGenerator(new SecureRandom());
        ClasspathCommonPasswords commonPasswords = new ClasspathCommonPasswords();
        AccessId accessId = new AccessId(100000);

        for (int i = 0; i < 20000; i++) {
            assertThat(PasswordPolicy.check(generator.next(), accessId, commonPasswords)).isEmpty();
        }
    }

    @Test
    void initialPasswordVaries() {
        RandomInitialPasswordGenerator generator = new RandomInitialPasswordGenerator(new SecureRandom());
        Set<String> values = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            values.add(generator.next().value());
        }

        assertThat(values).hasSize(50);
    }

    @Test
    void sessionTokenHas256Bits() {
        IssuedSessionToken issued = new Sha256SessionTokenIssuer(new SecureRandom()).issue();

        assertThat(Base64.getUrlDecoder().decode(issued.token())).hasSize(32);
    }

    @Test
    void sessionTokenIsUrlSafeWithoutPadding() {
        IssuedSessionToken issued = new Sha256SessionTokenIssuer(new SecureRandom()).issue();

        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void sessionTokenHashIsSha256OfTheToken() throws NoSuchAlgorithmException {
        IssuedSessionToken issued = new Sha256SessionTokenIssuer(new SecureRandom()).issue();

        byte[] expected = MessageDigest.getInstance("SHA-256").digest(issued.token().getBytes(StandardCharsets.UTF_8));

        assertThat(issued.hash()).isEqualTo(expected);
    }

    @Test
    void sessionTokenCanBeHashedAgain() {
        Sha256SessionTokenIssuer issuer = new Sha256SessionTokenIssuer(new SecureRandom());
        IssuedSessionToken issued = issuer.issue();

        assertThat(issuer.hash(issued.token())).isEqualTo(issued.hash());
    }

    @Test
    void sessionTokensAreUnique() {
        Sha256SessionTokenIssuer issuer = new Sha256SessionTokenIssuer(new SecureRandom());

        assertThat(issuer.issue().token()).isNotEqualTo(issuer.issue().token());
    }
}
