package io.github.escumartin.neocatecumenal.community;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.NeocatecumenalApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class CommunityModuleSkeletonTest {

    private final ApplicationModules modules = ApplicationModules.of(NeocatecumenalApplication.class);

    @Test
    void communityIsAnApplicationModule() {
        assertThat(modules.getModuleByName("community")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity", "spaces", "agenda"})
    void otherModulesDoNotDependOnCommunity(String name) {
        ApplicationModule community = modules.getModuleByName("community").orElseThrow();
        ApplicationModule other = modules.getModuleByName(name).orElseThrow();

        assertThat(other.getDirectDependencies(modules).contains(community)).isFalse();
    }
}
