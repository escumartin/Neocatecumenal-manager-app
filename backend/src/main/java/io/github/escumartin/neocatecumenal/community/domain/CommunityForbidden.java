package io.github.escumartin.neocatecumenal.community.domain;

public class CommunityForbidden extends CommunityDomainException {

    public CommunityForbidden() {
        super("FORBIDDEN");
    }
}
