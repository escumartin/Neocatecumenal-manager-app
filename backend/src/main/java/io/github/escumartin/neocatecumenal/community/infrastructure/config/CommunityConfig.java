package io.github.escumartin.neocatecumenal.community.infrastructure.config;

import io.github.escumartin.neocatecumenal.community.application.port.in.CreateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.ListCommunities;
import io.github.escumartin.neocatecumenal.community.application.port.in.ProvisionCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.ReactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.UpdateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityHistory;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.application.service.CreateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DeactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DeleteCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.GetCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.ListCommunitiesService;
import io.github.escumartin.neocatecumenal.community.application.service.ProvisionCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.ReactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.UpdateCommunityService;
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
    GetCommunity getCommunity(CommunityRepository communities) {
        return new GetCommunityService(communities);
    }

    @Bean
    ProvisionCommunity provisionCommunity(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        return new ProvisionCommunityService(communities, catalog);
    }
}
