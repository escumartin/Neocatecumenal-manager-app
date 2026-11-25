package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import io.github.escumartin.neocatecumenal.identity.CommunityCatalog;
import io.github.escumartin.neocatecumenal.identity.CommunityProvisioner;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.UserLinkGuard;
import io.github.escumartin.neocatecumenal.identity.application.port.in.AuthenticateSession;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapIdentity;
import io.github.escumartin.neocatecumenal.identity.application.port.in.GetMyProfile;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.DeleteUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.GetUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LogIn;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LogOut;
import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredAccessData;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IdentityEventPublisher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.LoginThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordChangeAttempts;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.application.service.AuthenticateSessionService;
import io.github.escumartin.neocatecumenal.identity.application.service.BootstrapIdentityService;
import io.github.escumartin.neocatecumenal.identity.application.service.GetMyProfileService;
import io.github.escumartin.neocatecumenal.identity.application.service.CreateUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.DeleteUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.GetUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.ListUsersService;
import io.github.escumartin.neocatecumenal.identity.application.service.UpdateUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.CommunityCatalogService;
import io.github.escumartin.neocatecumenal.identity.application.service.IdentityApiService;
import io.github.escumartin.neocatecumenal.identity.application.service.PermissiveUserLinkGuard;
import io.github.escumartin.neocatecumenal.identity.application.service.LogInService;
import io.github.escumartin.neocatecumenal.identity.application.service.LogOutService;
import io.github.escumartin.neocatecumenal.identity.application.service.PurgeExpiredAccessDataService;
import io.github.escumartin.neocatecumenal.identity.application.port.in.AcceptPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ChangeOwnPassword;
import io.github.escumartin.neocatecumenal.identity.application.port.in.RejectPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ResetUserPassword;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ResolvePendingSteps;
import io.github.escumartin.neocatecumenal.identity.application.service.AcceptPrivacyNoticeService;
import io.github.escumartin.neocatecumenal.identity.application.service.ChangeOwnPasswordService;
import io.github.escumartin.neocatecumenal.identity.application.service.RejectPrivacyNoticeService;
import io.github.escumartin.neocatecumenal.identity.application.service.ResetUserPasswordService;
import io.github.escumartin.neocatecumenal.identity.application.service.ResolvePendingStepsService;
import io.github.escumartin.neocatecumenal.identity.domain.CommonPasswords;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

@Configuration
class IdentityServicesConfig {

    @Bean
    LogIn logIn(UserRepository users, CommunityRepository communities, SessionRepository sessions,
            PasswordHasher hasher, SessionTokenIssuer tokens, LoginThrottleStore throttle,
            PrivacyNoticeSettings privacyNotice, Clock clock) {
        return new LogInService(users, communities, sessions, hasher, tokens, throttle, privacyNotice, clock);
    }

    @Bean
    LogOut logOut(CurrentSessionContext currentSession, SessionRepository sessions) {
        return new LogOutService(currentSession, sessions);
    }

    @Bean
    AuthenticateSession authenticateSession(SessionRepository sessions, UserRepository users,
            SessionTokenIssuer tokens, Clock clock) {
        return new AuthenticateSessionService(sessions, users, tokens, clock);
    }

    @Bean
    GetMyProfile getMyProfile(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice) {
        return new GetMyProfileService(currentSession, users, communities, privacyNotice);
    }

    @Bean
    IdentityApi identityApi(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice, UpdateUser updateUser, DeleteUser deleteUser) {
        return new IdentityApiService(currentSession, users, communities, privacyNotice, updateUser, deleteUser);
    }

    @Bean
    CommunityCatalog communityCatalog(CommunityRepository communities) {
        return new CommunityCatalogService(communities);
    }

    @Bean
    UserLinkGuard permissiveUserLinkGuard() {
        return new PermissiveUserLinkGuard();
    }

    @Bean
    CreateUser createUser(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PasswordHasher hasher, AccessIdGenerator accessIds,
            InitialPasswordGenerator passwords, PrivacyNoticeSettings privacyNotice,
            TransactionOperations transactions) {
        return new CreateUserService(currentSession, users, communities, hasher, accessIds, passwords, privacyNotice,
                transactions);
    }

    @Bean
    UpdateUser updateUser(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice, UserLinkGuard guard) {
        return new UpdateUserService(currentSession, users, communities, privacyNotice, guard);
    }

    @Bean
    DeleteUser deleteUser(CurrentSessionContext currentSession, UserRepository users, SessionRepository sessions,
            IdentityEventPublisher events, PrivacyNoticeSettings privacyNotice, UserLinkGuard guard) {
        return new DeleteUserService(currentSession, users, sessions, events, privacyNotice, guard);
    }

    @Bean
    ChangeOwnPassword changeOwnPassword(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions, PasswordHasher hasher, CommonPasswords commonPasswords,
            PrivacyNoticeSettings privacyNotice, PasswordChangeAttempts attempts) {
        return new ChangeOwnPasswordService(currentSession, users, sessions, hasher, commonPasswords, privacyNotice,
                attempts);
    }

    @Bean
    ResetUserPassword resetUserPassword(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions, PasswordHasher hasher, InitialPasswordGenerator passwords,
            PrivacyNoticeSettings privacyNotice) {
        return new ResetUserPasswordService(currentSession, users, sessions, hasher, passwords, privacyNotice);
    }

    @Bean
    AcceptPrivacyNotice acceptPrivacyNotice(CurrentSessionContext currentSession, UserRepository users,
            PrivacyNoticeSettings privacyNotice, Clock clock) {
        return new AcceptPrivacyNoticeService(currentSession, users, privacyNotice, clock);
    }

    @Bean
    RejectPrivacyNotice rejectPrivacyNotice(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions) {
        return new RejectPrivacyNoticeService(currentSession, users, sessions);
    }

    @Bean
    ResolvePendingSteps resolvePendingSteps(CurrentSessionContext currentSession, UserRepository users,
            PrivacyNoticeSettings privacyNotice) {
        return new ResolvePendingStepsService(currentSession, users, privacyNotice);
    }

    @Bean
    ListUsers listUsers(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice) {
        return new ListUsersService(currentSession, users, communities, privacyNotice);
    }

    @Bean
    GetUser getUser(CurrentSessionContext currentSession, UserRepository users, PrivacyNoticeSettings privacyNotice) {
        return new GetUserService(currentSession, users, privacyNotice);
    }

    @Bean
    PurgeExpiredAccessData purgeExpiredAccessData(SessionRepository sessions, LoginThrottleStore throttle,
            Clock clock) {
        return new PurgeExpiredAccessDataService(sessions, throttle, clock);
    }

    @Bean
    BootstrapIdentity bootstrapIdentity(UserRepository users, CommunityRepository communities,
            PasswordHasher hasher, AccessIdGenerator accessIds, InitialPasswordGenerator passwords,
            CommunityProvisioner provisioner) {
        return new BootstrapIdentityService(users, communities, hasher, accessIds, passwords, provisioner);
    }
}
