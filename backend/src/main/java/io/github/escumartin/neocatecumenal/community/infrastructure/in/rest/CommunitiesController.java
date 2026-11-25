package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.CommunitiesApi;
import io.github.escumartin.neocatecumenal.generated.model.Community;
import io.github.escumartin.neocatecumenal.generated.model.CommunityDeactivation;
import io.github.escumartin.neocatecumenal.generated.model.CommunityDetail;
import io.github.escumartin.neocatecumenal.generated.model.CommunityInput;
import io.github.escumartin.neocatecumenal.generated.model.ResponsibleDesignation;
import io.github.escumartin.neocatecumenal.community.application.port.in.CreateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DesignateResponsible;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.ListCommunities;
import io.github.escumartin.neocatecumenal.community.application.port.in.ReactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.UpdateCommunity;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunitiesController implements CommunitiesApi {

    private final ActorResolver actors;
    private final TransactionRetry retry;
    private final ListCommunities listCommunities;
    private final CreateCommunity createCommunity;
    private final GetCommunity getCommunity;
    private final UpdateCommunity updateCommunity;
    private final DeleteCommunity deleteCommunity;
    private final DeactivateCommunity deactivateCommunity;
    private final ReactivateCommunity reactivateCommunity;
    private final DesignateResponsible designateResponsible;

    CommunitiesController(ActorResolver actors, TransactionRetry retry, ListCommunities listCommunities,
            CreateCommunity createCommunity, GetCommunity getCommunity, UpdateCommunity updateCommunity,
            DeleteCommunity deleteCommunity, DeactivateCommunity deactivateCommunity,
            ReactivateCommunity reactivateCommunity, DesignateResponsible designateResponsible) {
        this.actors = actors;
        this.retry = retry;
        this.listCommunities = listCommunities;
        this.createCommunity = createCommunity;
        this.getCommunity = getCommunity;
        this.updateCommunity = updateCommunity;
        this.deleteCommunity = deleteCommunity;
        this.deactivateCommunity = deactivateCommunity;
        this.reactivateCommunity = reactivateCommunity;
        this.designateResponsible = designateResponsible;
    }

    @Override
    public ResponseEntity<List<Community>> listCommunities(Boolean includeDeactivated) {
        Actor actor = actors.currentActor();
        return ResponseEntity.ok(listCommunities.list(actor, Boolean.TRUE.equals(includeDeactivated)).stream()
                .map(CommunitiesMapper::toSummary).toList());
    }

    @Override
    public ResponseEntity<CommunityDetail> createCommunity(CommunityInput communityInput) {
        Actor actor = actors.currentActor();
        CommunityDetail created = CommunitiesMapper.toDetail(retry.execute(() -> createCommunity.create(actor,
                CommunitiesMapper.toNumber(communityInput.getNumber()),
                CommunitiesMapper.toName(communityInput.getName()))));
        return ResponseEntity.created(URI.create("/api/communities/" + created.getId())).body(created);
    }

    @Override
    public ResponseEntity<CommunityDetail> getCommunity(UUID communityId) {
        return ResponseEntity.ok(CommunitiesMapper.toDetail(
                getCommunity.get(actors.currentActor(), new CommunityId(communityId))));
    }

    @Override
    public ResponseEntity<CommunityDetail> updateCommunity(UUID communityId, CommunityInput communityInput) {
        Actor actor = actors.currentActor();
        return ResponseEntity.ok(CommunitiesMapper.toDetail(retry.execute(() -> updateCommunity.update(actor,
                new CommunityId(communityId), CommunitiesMapper.toNumber(communityInput.getNumber()),
                CommunitiesMapper.toName(communityInput.getName())))));
    }

    @Override
    public ResponseEntity<Void> deleteCommunity(UUID communityId) {
        Actor actor = actors.currentActor();
        retry.run(() -> deleteCommunity.delete(actor, new CommunityId(communityId)));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deactivateCommunity(UUID communityId, CommunityDeactivation communityDeactivation) {
        Actor actor = actors.currentActor();
        retry.run(() -> deactivateCommunity.deactivate(actor, new CommunityId(communityId),
                Boolean.TRUE.equals(communityDeactivation.getConfirmed())));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> reactivateCommunity(UUID communityId) {
        Actor actor = actors.currentActor();
        retry.run(() -> reactivateCommunity.reactivate(actor, new CommunityId(communityId)));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CommunityDetail> designateResponsible(UUID communityId,
            ResponsibleDesignation responsibleDesignation) {
        Actor actor = actors.currentActor();
        MemberId memberId = new MemberId(responsibleDesignation.getMemberId());
        try {
            return ResponseEntity.ok(CommunitiesMapper.toDetail(retry.execute(() -> designateResponsible.designate(
                    actor, new CommunityId(communityId), memberId))));
        } catch (CommunityDomainException rule) {
            if ("MEMBER_NOT_IN_COMMUNITY".equals(rule.code())) {
                throw new CommunityFieldInvalid("memberId", rule.code());
            }
            throw rule;
        }
    }
}
