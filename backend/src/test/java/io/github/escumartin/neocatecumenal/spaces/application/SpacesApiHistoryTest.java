package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.BookRoomForEvent;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.service.SpacesApiAdapter;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpacesApiHistoryTest {

    private final BookingRequestRepository requests = mock(BookingRequestRepository.class);
    private final BookRoomForEvent unused = mock(BookRoomForEvent.class);
    private final SpacesApiAdapter api = new SpacesApiAdapter(unused, requests);
    private final UUID community = UUID.randomUUID();

    @Test
    void reportsHistoryWhenTheCommunityHasBookingRequests() {
        when(requests.existsByCommunity(new CommunityId(community))).thenReturn(true);

        assertThat(api.hasBookingRequestsFor(community)).isTrue();
    }

    @Test
    void reportsNoHistoryWhenTheCommunityHasNoBookingRequests() {
        when(requests.existsByCommunity(new CommunityId(community))).thenReturn(false);

        assertThat(api.hasBookingRequestsFor(community)).isFalse();
    }

    @Test
    void asksTheRepositoryAboutTheGivenCommunityOnly() {
        api.hasBookingRequestsFor(community);

        verify(requests).existsByCommunity(new CommunityId(community));
    }
}
