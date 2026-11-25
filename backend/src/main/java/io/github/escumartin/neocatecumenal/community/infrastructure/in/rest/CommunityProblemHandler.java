package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.github.escumartin.neocatecumenal.community")
public class CommunityProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(CommunityProblemHandler.class);

    private static final Map<String, String> CONFLICT_TITLES = Map.ofEntries(
            Map.entry("COMMUNITY_HAS_RESPONSIBLE", "La comunidad tiene responsable"),
            Map.entry("COMMUNITY_NOT_EMPTY", "La comunidad tiene hermanos o usuarios"),
            Map.entry("COMMUNITY_HAS_HISTORY", "La comunidad tiene historial"),
            Map.entry("COMMUNITY_HAS_PRIVILEGED_USERS", "La comunidad tiene moderadores o administradores"),
            Map.entry("COMMUNITY_DEACTIVATED", "La comunidad está desactivada"),
            Map.entry("RESPONSIBLE_REQUIRED", "El hermano es el responsable de la comunidad"),
            Map.entry("MEMBER_ALREADY_LINKED", "El hermano ya tiene un usuario vinculado"),
            Map.entry("USER_ALREADY_LINKED", "El usuario ya está vinculado a otro hermano"),
            Map.entry("USER_LINKED_TO_MEMBER", "El usuario está vinculado a un hermano"),
            Map.entry("MEMBER_NOT_LINKED", "El hermano no tiene usuario vinculado"),
            Map.entry("CONCURRENT_MODIFICATION", "Los datos han cambiado mientras se editaban"),
            Map.entry("SELF_ACTION_FORBIDDEN", "Acción no permitida sobre uno mismo"),
            Map.entry("LAST_MODERATOR", "La comunidad se quedaría sin moderador"),
            Map.entry("LAST_ADMINISTRATOR", "El sistema se quedaría sin administrador"));

    @ExceptionHandler(CommunityFieldInvalid.class)
    public ResponseEntity<Map<String, Object>> invalidField(CommunityFieldInvalid exception) {
        Map<String, Object> body = body(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos", "VALIDATION_FAILED");
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", exception.field());
        error.put("code", exception.code());
        body.put("errors", List.of(error));
        return send(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", body);
    }

    @ExceptionHandler(CommunityDomainException.class)
    public ResponseEntity<Map<String, Object>> domainRule(CommunityDomainException exception) {
        String code = exception.code();
        return switch (code) {
            case "FORBIDDEN" -> respond(HttpStatus.FORBIDDEN, "Acceso denegado", code);
            case "CONSENT_MANAGED_BY_MEMBER" ->
                    respond(HttpStatus.FORBIDDEN, "El consentimiento lo gestiona el propio hermano", code);
            case "NOT_FOUND" -> respond(HttpStatus.NOT_FOUND, "Recurso no encontrado", code);
            case "UNAUTHENTICATED" -> respond(HttpStatus.UNAUTHORIZED, "Autenticación requerida", code);
            default -> CONFLICT_TITLES.containsKey(code)
                    ? respond(HttpStatus.CONFLICT, CONFLICT_TITLES.get(code), code)
                    : respond(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "INTERNAL_ERROR");
        };
    }

    @ExceptionHandler(IdentityRuleViolation.class)
    public ResponseEntity<Map<String, Object>> identityRule(IdentityRuleViolation exception) {
        String code = exception.code();
        if ("FORBIDDEN".equals(code)) {
            return respond(HttpStatus.FORBIDDEN, "Acceso denegado", code);
        }
        String problemCode = "COMMUNITY_HAS_USERS".equals(code) ? "COMMUNITY_NOT_EMPTY" : code;
        String title = CONFLICT_TITLES.get(problemCode);
        if (title == null) {
            return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "INTERNAL_ERROR");
        }
        return respond(HttpStatus.CONFLICT, title, problemCode);
    }

    @ExceptionHandler({StaleLockOrderException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> concurrentModification() {
        return respond(HttpStatus.CONFLICT, CONFLICT_TITLES.get("CONCURRENT_MODIFICATION"),
                "CONCURRENT_MODIFICATION");
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String title, String code) {
        return send(status, code, body(status, title, code));
    }

    private ResponseEntity<Map<String, Object>> send(HttpStatus status, String code, Map<String, Object> body) {
        log.atWarn().addKeyValue("code", code).addKeyValue("status", status.value())
                .log("Respuesta de error enviada");
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static Map<String, Object> body(HttpStatus status, String title, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("code", code);
        return body;
    }
}
