package io.github.escumartin.neocatecumenal.identity.infrastructure.in.security;

import io.github.escumartin.neocatecumenal.identity.application.port.in.ResolvePendingSteps;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ProblemDetailsHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class PendingStepsAuthorization extends OncePerRequestFilter {

    private static final String ME = "/api/me";
    private static final String CURRENT_SESSION = "/api/sessions/current";
    private static final String PRIVACY_ACCEPTANCE = "/api/me/privacy-notice-acceptance";
    private static final String PRIVACY_REJECTION = "/api/me/privacy-notice-rejection";
    private static final String PASSWORD = "/api/me/password";

    private final ResolvePendingSteps pendingSteps;
    private final ProblemDetailsHandler problems;

    PendingStepsAuthorization(ResolvePendingSteps pendingSteps, ProblemDetailsHandler problems) {
        this.pendingSteps = pendingSteps;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof SessionAuthentication)) {
            chain.doFilter(request, response);
            return;
        }
        List<PendingStep> pending = pendingSteps.ofCurrentSession();
        if (pending.isEmpty() || isReachable(request, pending)) {
            chain.doFilter(request, response);
            return;
        }
        if (pending.get(0) == PendingStep.PRIVACY_NOTICE) {
            problems.writeForbidden(response, "Debe aceptar el aviso de datos", "PRIVACY_NOTICE_PENDING");
        } else {
            problems.writeForbidden(response, "Debe cambiar la contraseña inicial", "PASSWORD_CHANGE_REQUIRED");
        }
    }

    private static boolean isReachable(HttpServletRequest request, List<PendingStep> pending) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (HttpMethod.GET.matches(method) && ME.equals(path)) {
            return true;
        }
        if (HttpMethod.DELETE.matches(method) && CURRENT_SESSION.equals(path)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && (PRIVACY_ACCEPTANCE.equals(path) || PRIVACY_REJECTION.equals(path))) {
            return true;
        }
        return HttpMethod.PUT.matches(method) && PASSWORD.equals(path)
                && !pending.contains(PendingStep.PRIVACY_NOTICE);
    }
}
