package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.SessionsApi;
import io.github.escumartin.neocatecumenal.generated.model.LoginRequest;
import io.github.escumartin.neocatecumenal.generated.model.LoginResponse;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LogIn;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LogOut;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginResult;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.ClientIpResolver;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.OriginCheck;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.SessionCookies;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionsController implements SessionsApi {

    private final LogIn logIn;
    private final LogOut logOut;
    private final ClientIpResolver clientIps;
    private final SessionCookies cookies;
    private final OriginCheck originCheck;
    private final HttpServletRequest request;
    private final MeterRegistry meters;

    SessionsController(LogIn logIn, LogOut logOut, ClientIpResolver clientIps, SessionCookies cookies,
            OriginCheck originCheck, HttpServletRequest request, MeterRegistry meters) {
        this.logIn = logIn;
        this.logOut = logOut;
        this.clientIps = clientIps;
        this.cookies = cookies;
        this.originCheck = originCheck;
        this.request = request;
        this.meters = meters;
    }

    @Override
    public ResponseEntity<LoginResponse> logIn(LoginRequest loginRequest) {
        boolean cookieDelivery = loginRequest.getTokenDelivery() == LoginRequest.TokenDeliveryEnum.COOKIE;
        if (cookieDelivery) {
            originCheck.require(request.getHeader(HttpHeaders.ORIGIN));
        }
        LoginResult result = logIn.logIn(new LoginCommand(new AccessId(loginRequest.getAccessId()),
                new PlainPassword(loginRequest.getPassword()), clientIps.resolve(request)));
        return switch (result) {
            case LoginResult.LoggedIn loggedIn -> {
                countLogin("SUCCESS");
                yield loggedIn(loggedIn, cookieDelivery);
            }
            case LoginResult.InvalidCredentials ignored -> {
                countLogin("INVALID_CREDENTIALS");
                throw new InvalidCredentialsException();
            }
            case LoginResult.Blocked blocked -> {
                countLogin("BLOCKED");
                throw new LoginBlockedException(blocked.retryAfter());
            }
        };
    }

    @Override
    public ResponseEntity<Void> logOut() {
        logOut.logOut();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.NO_CONTENT);
        if (cookies.read(request).isPresent()) {
            response.header(HttpHeaders.SET_COOKIE, cookies.clear());
        }
        return response.build();
    }

    private void countLogin(String result) {
        meters.counter("identity.login", "result", result).increment();
    }

    private ResponseEntity<LoginResponse> loggedIn(LoginResult.LoggedIn loggedIn, boolean cookieDelivery) {
        LoginResponse body = new LoginResponse(MeMapper.toMe(loggedIn.profile()));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (cookieDelivery) {
            response.header(HttpHeaders.SET_COOKIE, cookies.issue(loggedIn.token()));
        } else {
            body.setToken(loggedIn.token());
        }
        return response.body(body);
    }
}
