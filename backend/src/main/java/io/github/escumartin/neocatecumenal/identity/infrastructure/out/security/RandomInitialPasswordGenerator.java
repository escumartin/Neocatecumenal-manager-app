package io.github.escumartin.neocatecumenal.identity.infrastructure.out.security;

import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomInitialPasswordGenerator implements InitialPasswordGenerator {

    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;
    private static final int LENGTH = 10;
    private static final int MAX_DIGIT_RUN = 5;

    private final SecureRandom random;

    public RandomInitialPasswordGenerator() {
        this(new SecureRandom());
    }

    public RandomInitialPasswordGenerator(SecureRandom random) {
        this.random = random;
    }

    @Override
    public PlainPassword next() {
        StringBuilder password = new StringBuilder(LENGTH);
        int digitRun = 0;
        for (int i = 0; i < LENGTH; i++) {
            char next = digitRun == MAX_DIGIT_RUN
                    ? LETTERS.charAt(random.nextInt(LETTERS.length()))
                    : ALPHABET.charAt(random.nextInt(ALPHABET.length()));
            digitRun = DIGITS.indexOf(next) >= 0 ? digitRun + 1 : 0;
            password.append(next);
        }
        return new PlainPassword(password.toString());
    }
}
