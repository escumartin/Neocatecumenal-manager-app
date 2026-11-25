package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.generated.model.ContactFieldInput;
import io.github.escumartin.neocatecumenal.generated.model.Member;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class MembersMapper {

    private MembersMapper() {
    }

    static PersonName toName(String givenName, String surnames) {
        return PersonName.of(givenName, surnames);
    }

    static ContactChange toContactChange(ContactFieldInput mobile, ContactFieldInput landline,
            ContactFieldInput email) {
        Map<ContactField, FieldChange> entries = new EnumMap<>(ContactField.class);
        entries.put(ContactField.MOBILE, toFieldChange(mobile));
        entries.put(ContactField.LANDLINE, toFieldChange(landline));
        entries.put(ContactField.EMAIL, toFieldChange(email));
        return ContactChange.of(entries);
    }

    static Optional<OnBehalfConsent> toConsent(
            io.github.escumartin.neocatecumenal.generated.model.OnBehalfConsent consent) {
        return Optional.ofNullable(consent).map(given -> new OnBehalfConsent(
                ConsentChannel.valueOf(given.getChannel().name()), Boolean.TRUE.equals(given.getNoticeGiven())));
    }

    static Member toApi(MemberView view, Actor actor) {
        io.github.escumartin.neocatecumenal.community.domain.Member member = view.member();
        Member api = new Member(member.id().value(), member.communityId().value(), member.name().givenName(),
                view.responsible())
                .surnames(member.name().surnames().orElse(null))
                .mobile(member.contact().get(ContactField.MOBILE))
                .landline(member.contact().get(ContactField.LANDLINE))
                .email(member.contact().get(ContactField.EMAIL));
        if (actor.role() != ActorRole.REGISTERED) {
            api.linkedUserId(member.linkedUserId().map(UserId::value).orElse(null));
        }
        return api;
    }

    private static FieldChange toFieldChange(ContactFieldInput input) {
        return Boolean.TRUE.equals(input.getShared()) ? FieldChange.share(input.getValue())
                : FieldChange.withdraw();
    }
}
