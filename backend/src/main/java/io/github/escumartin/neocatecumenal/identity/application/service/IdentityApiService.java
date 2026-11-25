package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.Role;
import io.github.escumartin.neocatecumenal.identity.UserRef;
import io.github.escumartin.neocatecumenal.identity.application.port.in.DeleteUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class IdentityApiService implements IdentityApi {

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final CommunityRepository communities;
    private final PrivacyNoticeSettings privacyNotice;
    private final UpdateUser updateUser;
    private final DeleteUser deleteUser;

    public IdentityApiService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice,
            UpdateUser updateUser, DeleteUser deleteUser) {
        this.currentSession = currentSession;
        this.users = users;
        this.communities = communities;
        this.privacyNotice = privacyNotice;
        this.updateUser = updateUser;
        this.deleteUser = deleteUser;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentUser> currentUser() {
        return currentSession.current()
                .flatMap(session -> users.findById(session.userId()))
                .filter(user -> user.pendingSteps(privacyNotice.currentVersion()).isEmpty())
                .map(IdentityApiService::toCurrentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityRef> communities() {
        return communities.findAll().stream()
                .map(community -> new CommunityRef(community.id().value(), community.number().value(), community.active()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityRef> community(UUID id) {
        return communities.findById(new CommunityId(id))
                .map(community -> new CommunityRef(community.id().value(), community.number().value(), community.active()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> user(UUID id) {
        return users.findById(new UserId(id)).map(IdentityApiService::toRef);
    }

    @Override
    @Transactional
    public Optional<UserRef> lockUser(UUID id) {
        return users.lockForShare(new UserId(id)).map(IdentityApiService::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRef> usersOf(UUID communityId) {
        return users.findAllByCommunity(new CommunityId(communityId)).stream()
                .map(IdentityApiService::toRef)
                .toList();
    }

    @Override
    @Transactional
    public void moveUser(UUID userId, UUID communityId) {
        User target = users.findById(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        translatingViolations(() -> updateUser.update(new UpdateUserCommand(target.id(), target.name(),
                target.role(), new CommunityId(communityId))));
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        translatingViolations(() -> deleteUser.delete(new UserId(userId)));
    }

    private static void translatingViolations(Runnable action) {
        try {
            action.run();
        } catch (DomainException violation) {
            throw new IdentityRuleViolation(violation.code());
        } catch (FieldValidationException violation) {
            throw new IdentityRuleViolation(violation.code());
        }
    }

    private static UserRef toRef(User user) {
        return new UserRef(user.id().value(), user.name().value(), Role.valueOf(user.role().name()),
                user.communityId().value());
    }

    private static CurrentUser toCurrentUser(User user) {
        return new CurrentUser(user.id().value(), user.accessId().value(), user.name().value(),
                Role.valueOf(user.role().name()), user.communityId().value());
    }
}
