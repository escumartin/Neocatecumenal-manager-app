package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestCommunityProvisionerConfig {

    @Bean
    CommunityProvisioner testCommunityProvisioner(IdentityApi identity, CommunityCatalog catalog) {
        return number -> identity.communities().stream()
                .filter(community -> community.number() == number)
                .map(CommunityRef::id)
                .findFirst()
                .orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    catalog.register(id, number);
                    return id;
                });
    }

    @Bean
    UserLinkGuard testUserLinkGuard() {
        return new UserLinkGuard() {
            @Override
            public boolean allowsCommunityChange(UUID userId, UUID currentCommunityId, UUID targetCommunityId) {
                return true;
            }

            @Override
            public void releaseBeforeDeletion(UUID userId, UUID currentCommunityId) {
            }
        };
    }
}
