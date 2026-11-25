package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberProblemDetailsTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final List<String> PERSONAL_DATA = List.of("Zenobia", "Quirógrafo", "677123456", "+34988765432",
            "zenobia.q@example.test", "512345678", "a@b");
    private static final String VERBAL = "\"onBehalfConsent\":{\"channel\":\"VERBAL\",\"noticeGiven\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    private UUID home;
    private String moderator;

    @BeforeEach
    void setUp() {
        CommunityApiFixture fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
    }

    static Stream<String> failingBodies() {
        return Stream.of(
                "invalidPhone", "invalidEmail", "noConsent", "noNotice", "tooLongSurnames", "malformed");
    }

    private String body(String scenario) {
        String head = "{\"communityId\":\"" + home + "\",\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\"";
        return switch (scenario) {
            case "invalidPhone" -> head + ",\"contact\":" + contact("512345678", null, null) + "," + VERBAL + "}";
            case "invalidEmail" -> head + ",\"contact\":" + contact(null, null, "a@b") + "," + VERBAL + "}";
            case "noConsent" -> head + ",\"contact\":" + contact("677123456", "+34988765432",
                    "zenobia.q@example.test") + "}";
            case "noNotice" -> head + ",\"contact\":" + contact("677123456", null, null)
                    + ",\"onBehalfConsent\":{\"channel\":\"WRITTEN\",\"noticeGiven\":false}}";
            case "tooLongSurnames" -> "{\"communityId\":\"" + home + "\",\"givenName\":\"Zenobia\",\"surnames\":\""
                    + "Quirógrafo".repeat(11) + "\"}";
            default -> "{\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\",";
        };
    }

    private static String contact(String mobile, String landline, String email) {
        return "{\"mobile\":{\"shared\":" + (mobile != null) + (mobile != null ? ",\"value\":\"" + mobile + "\"" : "")
                + "},\"landline\":{\"shared\":" + (landline != null)
                + (landline != null ? ",\"value\":\"" + landline + "\"" : "") + "},\"email\":{\"shared\":"
                + (email != null) + (email != null ? ",\"value\":\"" + email + "\"" : "") + "}}";
    }

    @ParameterizedTest
    @MethodSource("failingBodies")
    void errorsOnCreateCarryOnlyCodesAndNeverPersonalData(String scenario) throws Exception {
        var response = mockMvc.perform(post("/api/members").header(HttpHeaders.AUTHORIZATION, moderator)
                .contentType(MediaType.APPLICATION_JSON).content(body(scenario))).andReturn().getResponse();

        String content = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(content).doesNotContain(PERSONAL_DATA);
        assertThat(JsonPath.<Object>read(content, "$.errors[0].code")).isNotNull();
        assertThat(content).doesNotContain("\"detail\"");
    }

    @ParameterizedTest
    @MethodSource("failingBodies")
    void errorsOnContactChangeCarryOnlyCodesAndNeverPersonalData(String scenario) throws Exception {
        UUID member = UUID.randomUUID();
        jdbc.update("insert into community_member (id, community_id, given_name, version) values (?, ?, 'Test', 0)",
                member, home);

        var response = mockMvc.perform(put("/api/members/" + member + "/contact")
                .header(HttpHeaders.AUTHORIZATION, moderator).contentType(MediaType.APPLICATION_JSON)
                .content(contactBody(scenario))).andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain(PERSONAL_DATA).doesNotContain("\"detail\"");
    }

    private String contactBody(String scenario) {
        return switch (scenario) {
            case "invalidPhone" -> contact("512345678", null, null);
            case "invalidEmail" -> contact(null, null, "a@b");
            case "noConsent" -> contact("677123456", "+34988765432", "zenobia.q@example.test");
            case "noNotice" -> withConsent(contact("677123456", null, null),
                    "{\"channel\":\"WRITTEN\",\"noticeGiven\":false}");
            case "tooLongSurnames" -> "{\"mobile\":{\"shared\":true,\"value\":\"" + "6".repeat(300) + "\"}}";
            default -> "{\"mobile\":{\"shared\":true,\"value\":\"677123456\"},";
        };
    }

    private static String withConsent(String contact, String consent) {
        return contact.substring(0, contact.length() - 1) + ",\"onBehalfConsent\":" + consent + "}";
    }
}
