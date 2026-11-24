package io.github.escumartin.neocatecumenal.identity.infrastructure.out.security;

import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private static final String PREFIX = "{argon2}";
    private static final int MEMORY_KIB = 19 * 1024;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final Pattern ENCODED = Pattern.compile(
            "^\\{argon2}\\$argon2id\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)$");

    private final SecureRandom random = new SecureRandom();
    private final PasswordHash dummyHash;

    public Argon2PasswordHasher() {
        this.dummyHash = hash(new PlainPassword("dummy-password-for-timing-equalisation"));
    }

    @Override
    public PasswordHash hash(PlainPassword password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = digest(password.value(), salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return new PasswordHash(PREFIX + "$argon2id$v=19$m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM
                + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(digest));
    }

    @Override
    public boolean matches(PlainPassword password, PasswordHash hash) {
        Matcher matcher = ENCODED.matcher(hash.value());
        if (!matcher.matches()) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(matcher.group(4));
        byte[] expected = Base64.getDecoder().decode(matcher.group(5));
        byte[] actual = digest(password.value(), salt, Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean matchesDummy(PlainPassword password) {
        matches(password, dummyHash);
        return false;
    }

    private byte[] digest(String password, byte[] salt, int memoryKib, int iterations, int parallelism, int length) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] output = new byte[length];
        generator.generateBytes(
                Normalizer.normalize(password, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8), output);
        return output;
    }
}
