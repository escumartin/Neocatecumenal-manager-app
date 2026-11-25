package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventCreationRaceIT {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(700000);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private BookingRequestRepository requests;

    private String moderator;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room, "
                + "identity_login_pair_throttle, identity_login_ip_throttle, identity_session, identity_user, "
                + "identity_community cascade");
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(1)));
        String administrator = bearerFor(UserRole.ADMINISTRATOR, community);
        moderator = bearerFor(UserRole.MODERATOR, community);
        String body = mockMvc.perform(asJson(post("/api/rooms"), administrator, "{\"name\":\"Main hall\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        roomId = JsonPath.read(body, "$.id");
    }

    private String bearerFor(UserRole role, Community community) {
        User user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()),
                new AccessId(ACCESS_IDS.incrementAndGet()), new DisplayName("Test Person"), role, community.id(),
                new PasswordHash("{argon2}hash"), false, new PrivacyConsent("v1", Instant.now())));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String eventJson(String start, String end) {
        String tomorrow = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(1).toString();
        return "{\"type\":\"WORD\",\"title\":\"Opening word\",\"roomId\":\"" + roomId + "\",\"date\":\"" + tomorrow
                + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void aSlotLostAtTheDatabaseInsideTheAgendaTransactionAnswersRoomOccupiedAndSavesNothing() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderator, eventJson("18:00", "20:00")))
                .andExpect(status().isCreated());
        doReturn(List.of()).when(requests).findConflicts(any(), any(), any());

        mockMvc.perform(asJson(post("/api/events"), moderator, eventJson("19:00", "21:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"));

        assertThat(count("event")).isEqualTo(1);
        assertThat(count("booking_request")).isEqualTo(1);
        assertThat(count("booking")).isEqualTo(1);
    }
}
