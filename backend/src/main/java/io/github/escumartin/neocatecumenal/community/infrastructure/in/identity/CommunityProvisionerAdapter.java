package io.github.escumartin.neocatecumenal.community.infrastructure.in.identity;

import io.github.escumartin.neocatecumenal.community.application.port.in.ProvisionCommunity;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.CommunityProvisioner;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CommunityProvisionerAdapter implements CommunityProvisioner {

    private final ProvisionCommunity provision;

    CommunityProvisionerAdapter(ProvisionCommunity provision) {
        this.provision = provision;
    }

    @Override
    public UUID ensure(int number) {
        return provision.ensure(new CommunityNumber(number)).value();
    }
}
