package io.github.escumartin.neocatecumenal.identity.infrastructure.in.security;

import io.github.escumartin.neocatecumenal.identity.application.port.in.AuthenticateSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticateSession authenticateSession;
    private final SessionCookies cookies;
    private final OriginCheck originCheck;
    private final AccessDeniedHandler accessDenied;

    SessionAuthenticationFilter(AuthenticateSession authenticateSession, SessionCookies cookies,
            OriginCheck originCheck, AccessDeniedHandler accessDenied) {
        this.authenticateSession = authenticateSession;
        this.cookies = cookies;
        this.originCheck = originCheck;
        this.accessDenied = accessDenied;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> bearer = bearerToken(request);
        Optional<String> cookie = bearer.isPresent() ? Optional.empty() : cookies.read(request);
        Optional<String> token = bearer.or(() -> cookie);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (cookie.isPresent() && originCheck.changesState(request.getMethod())
                && !originCheck.allows(request.getHeader(HttpHeaders.ORIGIN))) {
            accessDenied.handle(request, response, new AccessDeniedException("Origen no permitido"));
            return;
        }
        authenticateSession.authenticate(token.get()).ifPresent(session -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new SessionAuthentication(session));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
