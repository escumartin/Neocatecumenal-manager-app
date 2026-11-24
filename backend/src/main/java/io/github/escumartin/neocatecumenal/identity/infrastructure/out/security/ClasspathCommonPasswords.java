package io.github.escumartin.neocatecumenal.identity.infrastructure.out.security;

import io.github.escumartin.neocatecumenal.identity.domain.CommonPasswords;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ClasspathCommonPasswords implements CommonPasswords {

    private static final List<String> RESOURCES = List.of(
            "/identity/common-passwords.txt",
            "/identity/common-passwords-es.txt");

    private final Set<String> passwords;

    public ClasspathCommonPasswords() {
        Set<String> loaded = new HashSet<>();
        RESOURCES.forEach(resource -> loaded.addAll(load(resource)));
        this.passwords = Set.copyOf(loaded);
    }

    @Override
    public boolean contains(String lowerCasePassword) {
        return passwords.contains(lowerCasePassword);
    }

    private static Set<String> load(String resource) {
        InputStream stream = ClasspathCommonPasswords.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Falta el recurso de contraseñas comunes: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Set<String> entries = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = Normalizer.normalize(line.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
