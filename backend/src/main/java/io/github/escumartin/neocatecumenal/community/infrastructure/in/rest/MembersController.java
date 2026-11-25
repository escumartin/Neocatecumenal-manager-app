package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeMemberContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.CreateMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.ListMembers;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.in.RenameMember;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.generated.api.MembersApi;
import io.github.escumartin.neocatecumenal.generated.model.Member;
import io.github.escumartin.neocatecumenal.generated.model.MemberContactInput;
import io.github.escumartin.neocatecumenal.generated.model.MemberDataExport;
import io.github.escumartin.neocatecumenal.generated.model.MemberInput;
import io.github.escumartin.neocatecumenal.generated.model.MemberLink;
import io.github.escumartin.neocatecumenal.generated.model.MemberNameInput;
import io.github.escumartin.neocatecumenal.generated.model.MemberTransfer;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MembersController implements MembersApi {

    private final ActorResolver actors;
    private final TransactionRetry retry;
    private final ListMembers listMembers;
    private final CreateMember createMember;
    private final GetMember getMember;
    private final RenameMember renameMember;
    private final ChangeMemberContact changeMemberContact;

    MembersController(ActorResolver actors, TransactionRetry retry, ListMembers listMembers,
            CreateMember createMember, GetMember getMember, RenameMember renameMember,
            ChangeMemberContact changeMemberContact) {
        this.actors = actors;
        this.retry = retry;
        this.listMembers = listMembers;
        this.createMember = createMember;
        this.getMember = getMember;
        this.renameMember = renameMember;
        this.changeMemberContact = changeMemberContact;
    }

    @Override
    public ResponseEntity<List<Member>> listMembers(UUID communityId) {
        Actor actor = actors.currentActor();
        CommunityId target = communityToList(actor, communityId);
        return ResponseEntity.ok(listMembers.list(actor, target).stream()
                .map(view -> MembersMapper.toApi(view, actor)).toList());
    }

    @Override
    public ResponseEntity<Member> createMember(MemberInput memberInput) {
        Actor actor = actors.currentActor();
        MemberView created;
        try {
            created = retry.execute(() -> createMember.create(actor, new CommunityId(memberInput.getCommunityId()),
                    MembersMapper.toName(memberInput.getGivenName(), memberInput.getSurnames()),
                    Optional.ofNullable(memberInput.getContact()).map(contact -> MembersMapper.toContactChange(
                            contact.getMobile(), contact.getLandline(), contact.getEmail())),
                    MembersMapper.toConsent(memberInput.getOnBehalfConsent())));
        } catch (CommunityNotFound notFound) {
            throw new CommunityFieldInvalid("communityId", "COMMUNITY_NOT_FOUND");
        } catch (CommunityDomainException rule) {
            if ("COMMUNITY_DEACTIVATED".equals(rule.code())) {
                throw new CommunityFieldInvalid("communityId", "COMMUNITY_DEACTIVATED");
            }
            throw rule;
        }
        Member body = MembersMapper.toApi(created, actor);
        return ResponseEntity.created(URI.create("/api/members/" + body.getId())).body(body);
    }

    @Override
    public ResponseEntity<Member> getMember(UUID memberId) {
        Actor actor = actors.currentActor();
        return ResponseEntity.ok(MembersMapper.toApi(getMember.get(actor, new MemberId(memberId)), actor));
    }

    @Override
    public ResponseEntity<Member> renameMember(UUID memberId, MemberNameInput memberNameInput) {
        Actor actor = actors.currentActor();
        MemberView renamed = retry.execute(() -> renameMember.rename(actor, new MemberId(memberId),
                MembersMapper.toName(memberNameInput.getGivenName(), memberNameInput.getSurnames())));
        return ResponseEntity.ok(MembersMapper.toApi(renamed, actor));
    }

    @Override
    public ResponseEntity<Member> changeMemberContact(UUID memberId, MemberContactInput memberContactInput) {
        Actor actor = actors.currentActor();
        MemberView changed = retry.execute(() -> changeMemberContact.change(actor, new MemberId(memberId),
                MembersMapper.toContactChange(memberContactInput.getMobile(), memberContactInput.getLandline(),
                        memberContactInput.getEmail()),
                MembersMapper.toConsent(memberContactInput.getOnBehalfConsent())));
        return ResponseEntity.ok(MembersMapper.toApi(changed, actor));
    }

    @Override
    public ResponseEntity<Void> deleteMember(UUID memberId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<MemberDataExport> exportMemberData(UUID memberId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Member> linkMember(UUID memberId, MemberLink memberLink) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Void> unlinkMember(UUID memberId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Member> transferMember(UUID memberId, MemberTransfer memberTransfer) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private static CommunityId communityToList(Actor actor, UUID requested) {
        if (requested != null) {
            return new CommunityId(requested);
        }
        if (actor.role() == ActorRole.ADMINISTRATOR) {
            throw new CommunityFieldInvalid("communityId", "REQUIRED");
        }
        return actor.communityId();
    }
}
