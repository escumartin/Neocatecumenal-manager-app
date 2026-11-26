package io.github.escumartin.neocatecumenal.community.infrastructure.out.identity;

import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.CommunityCatalog;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import org.springframework.stereotype.Component;

@Component
public class IdentityCommunityCatalogAdapter implements IdentityCommunityCatalog {

    private static final String HAS_USERS = "COMMUNITY_HAS_USERS";

    private final CommunityCatalog catalog;

    IdentityCommunityCatalogAdapter(CommunityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void register(CommunityId id, CommunityNumber number) {
        catalog.register(id.value(), number.value());
    }

    @Override
    public void renumber(CommunityId id, CommunityNumber number) {
        catalog.renumber(id.value(), number.value());
    }

    @Override
    public void setActive(CommunityId id, boolean active) {
        catalog.setActive(id.value(), active);
    }

    @Override
    public void lockForRemoval(CommunityId id) {
        catalog.lockForRemoval(id.value());
    }

    @Override
    public void remove(CommunityId id) {
        try {
            catalog.remove(id.value());
        } catch (IdentityRuleViolation violation) {
            if (HAS_USERS.equals(violation.code())) {
                throw new CommunityDomainException("COMMUNITY_NOT_EMPTY");
            }
            throw violation;
        }
    }
}
