package io.github.escumartin.neocatecumenal.community.infrastructure.config;

import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeMemberContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeOwnContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.CreateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.CreateMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DesignateResponsible;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.ExportMemberData;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetOwnMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.GuardUserLink;
import io.github.escumartin.neocatecumenal.community.application.port.in.LinkMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.ListCommunities;
import io.github.escumartin.neocatecumenal.community.application.port.in.ListMembers;
import io.github.escumartin.neocatecumenal.community.application.port.in.ProvisionCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.ReactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.RenameMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.TransferMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.UnlinkMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.UpdateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityHistory;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeMemberContactService;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeOwnContactService;
import io.github.escumartin.neocatecumenal.community.application.service.CreateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.CreateMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.DeactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DeleteCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DesignateResponsibleService;
import io.github.escumartin.neocatecumenal.community.application.service.GetCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DeleteMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ExportMemberDataService;
import io.github.escumartin.neocatecumenal.community.application.service.GetMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.GetOwnMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.GuardUserLinkService;
import io.github.escumartin.neocatecumenal.community.application.service.LinkMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ListCommunitiesService;
import io.github.escumartin.neocatecumenal.community.application.service.ListMembersService;
import io.github.escumartin.neocatecumenal.community.application.service.ProvisionCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.ReactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.RenameMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.TransferMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.UnlinkMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.UpdateCommunityService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CommunityConfig {

    @Bean
    CreateCommunity createCommunity(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        return new CreateCommunityService(communities, catalog);
    }

    @Bean
    UpdateCommunity updateCommunity(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        return new UpdateCommunityService(communities, catalog);
    }

    @Bean
    DeactivateCommunity deactivateCommunity(CommunityRepository communities, UserDirectory users,
            IdentityCommunityCatalog catalog) {
        return new DeactivateCommunityService(communities, users, catalog);
    }

    @Bean
    ReactivateCommunity reactivateCommunity(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        return new ReactivateCommunityService(communities, catalog);
    }

    @Bean
    DeleteCommunity deleteCommunity(CommunityRepository communities, MemberRepository members, UserDirectory users,
            CommunityHistory history, IdentityCommunityCatalog catalog) {
        return new DeleteCommunityService(communities, members, users, history, catalog);
    }

    @Bean
    ListCommunities listCommunities(CommunityRepository communities) {
        return new ListCommunitiesService(communities);
    }

    @Bean
    GetCommunity getCommunity(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        return new GetCommunityService(communities, members, users);
    }

    @Bean
    DesignateResponsible designateResponsible(CommunityRepository communities, MemberRepository members,
            UserDirectory users) {
        return new DesignateResponsibleService(communities, members, users);
    }

    @Bean
    ProvisionCommunity provisionCommunity(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        return new ProvisionCommunityService(communities, catalog);
    }

    @Bean
    CreateMember createMember(CommunityRepository communities, MemberRepository members, Clock clock) {
        return new CreateMemberService(communities, members, clock);
    }

    @Bean
    RenameMember renameMember(CommunityRepository communities, MemberRepository members) {
        return new RenameMemberService(communities, members);
    }

    @Bean
    ChangeMemberContact changeMemberContact(CommunityRepository communities, MemberRepository members,
            Clock clock) {
        return new ChangeMemberContactService(communities, members, clock);
    }

    @Bean
    GetMember getMember(CommunityRepository communities, MemberRepository members) {
        return new GetMemberService(communities, members);
    }

    @Bean
    ListMembers listMembers(CommunityRepository communities, MemberRepository members) {
        return new ListMembersService(communities, members);
    }

    @Bean
    GetOwnMember getOwnMember(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        return new GetOwnMemberService(communities, members, users);
    }

    @Bean
    ChangeOwnContact changeOwnContact(CommunityRepository communities, MemberRepository members,
            UserDirectory users, Clock clock) {
        return new ChangeOwnContactService(communities, members, users, clock);
    }

    @Bean
    LinkMember linkMember(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        return new LinkMemberService(communities, members, users);
    }

    @Bean
    UnlinkMember unlinkMember(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        return new UnlinkMemberService(communities, members, users);
    }

    @Bean
    TransferMember transferMember(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        return new TransferMemberService(communities, members, users);
    }

    @Bean
    GuardUserLink guardUserLink(CommunityRepository communities, MemberRepository members) {
        return new GuardUserLinkService(communities, members);
    }

    @Bean
    DeleteMember deleteMember(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        return new DeleteMemberService(communities, members, users);
    }

    @Bean
    ExportMemberData exportMemberData(CommunityRepository communities, MemberRepository members,
            UserDirectory users, Clock clock) {
        return new ExportMemberDataService(communities, members, users, clock);
    }
}
