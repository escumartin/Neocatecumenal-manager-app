package io.github.escumartin.neocatecumenal;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesAreValid() {
        ApplicationModules.of(NeocatecumenalApplication.class).verify();
    }
}
