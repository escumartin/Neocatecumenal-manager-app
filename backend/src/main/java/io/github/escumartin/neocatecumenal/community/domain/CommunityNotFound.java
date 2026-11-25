package io.github.escumartin.neocatecumenal.community.domain;

public class CommunityNotFound extends CommunityDomainException {

    public CommunityNotFound() {
        super("NOT_FOUND");
    }
}
