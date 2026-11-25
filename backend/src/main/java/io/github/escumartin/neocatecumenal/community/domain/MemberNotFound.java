package io.github.escumartin.neocatecumenal.community.domain;

public class MemberNotFound extends CommunityDomainException {

    public MemberNotFound() {
        super("NOT_FOUND");
    }
}
