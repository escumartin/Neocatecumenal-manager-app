package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.ObjectMapper;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.OriginNotAllowedException;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice
public class ProblemDetailsHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    private final ObjectMapper objectMapper;

    public ProblemDetailsHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound() {
        return respond(HttpStatus.NOT_FOUND, "Recurso no encontrado", "NOT_FOUND");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> invalidCredentials() {
        return respond(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas", "INVALID_CREDENTIALS");
    }

    @ExceptionHandler(LoginBlockedException.class)
    public ResponseEntity<Map<String, Object>> loginBlocked(LoginBlockedException exception) {
        Map<String, Object> body = body(HttpStatus.TOO_MANY_REQUESTS, "Inicio de sesión bloqueado temporalmente",
                "LOGIN_TEMPORARILY_BLOCKED");
        body.put("retryAfterSeconds", exception.retryAfterSeconds());
        logProblem(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TEMPORARILY_BLOCKED");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(OriginNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> originNotAllowed() {
        return respond(HttpStatus.FORBIDDEN, "Acceso denegado", "FORBIDDEN");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidArguments(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), validationCode(error)))
                .sorted(Comparator.comparing((Map<String, String> error) -> error.get("field"))
                        .thenComparing(error -> error.get("code")))
                .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody() {
        return validationFailed(List.of(fieldError("body", "INVALID_FORMAT")));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> domainRule(DomainException exception) {
        return switch (exception.code()) {
            case "FORBIDDEN" -> respond(HttpStatus.FORBIDDEN, "Acceso denegado", "FORBIDDEN");
            case "UNAUTHENTICATED" -> respond(HttpStatus.UNAUTHORIZED, "Autenticación requerida", "UNAUTHENTICATED");
            case "PRIVACY_NOTICE_OUTDATED" -> respond(HttpStatus.CONFLICT, "La versión del aviso de datos ha cambiado",
                    "PRIVACY_NOTICE_OUTDATED");
            case "SELF_ACTION_FORBIDDEN" -> respond(HttpStatus.CONFLICT, "Acción no permitida sobre uno mismo",
                    "SELF_ACTION_FORBIDDEN");
            case "LAST_MODERATOR" -> respond(HttpStatus.CONFLICT, "La comunidad se quedaría sin moderador",
                    "LAST_MODERATOR");
            case "LAST_ADMINISTRATOR" -> respond(HttpStatus.CONFLICT, "El sistema se quedaría sin administrador",
                    "LAST_ADMINISTRATOR");
            case "REQUIRED", "INVALID_FORMAT", "TOO_LONG" ->
                    validationFailed(List.of(fieldError("body", exception.code())));
            default -> internalError(exception);
        };
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied() {
        return respond(HttpStatus.FORBIDDEN, "Acceso denegado", "FORBIDDEN");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> notAuthenticated() {
        return respond(HttpStatus.UNAUTHORIZED, "Autenticación requerida", "UNAUTHENTICATED");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            if (status.is5xxServerError()) {
                logUnexpected(exception, status);
                return ResponseEntity.status(status).headers(errorResponse.getHeaders())
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problemFor(status));
            }
            ResponseEntity<Map<String, Object>> problem = forStatus(status);
            return ResponseEntity.status(problem.getStatusCode()).headers(errorResponse.getHeaders())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem.getBody());
        }
        return internalError(exception);
    }

    public ResponseEntity<Map<String, Object>> forStatus(HttpStatus status) {
        if (status.is5xxServerError()) {
            return internalError(null);
        }
        logProblem(status, kindOf(status).code());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemFor(status));
    }

    public static Map<String, Object> problemFor(HttpStatus status) {
        Kind kind = kindOf(status);
        return body(status, kind.title(), kind.code());
    }

    private record Kind(String title, String code) {
    }

    private static Kind kindOf(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> new Kind("Autenticación requerida", "UNAUTHENTICATED");
            case FORBIDDEN -> new Kind("Acceso denegado", "FORBIDDEN");
            case NOT_FOUND -> new Kind("Recurso no encontrado", "NOT_FOUND");
            case METHOD_NOT_ALLOWED -> new Kind("Método no permitido", "METHOD_NOT_ALLOWED");
            case NOT_ACCEPTABLE -> new Kind("Formato de respuesta no soportado", "NOT_ACCEPTABLE");
            case UNSUPPORTED_MEDIA_TYPE -> new Kind("Tipo de contenido no soportado", "UNSUPPORTED_MEDIA_TYPE");
            default -> status.is4xxClientError()
                    ? new Kind("Petición no válida", "BAD_REQUEST")
                    : new Kind("Error interno", "INTERNAL_ERROR");
        };
    }

    private ResponseEntity<Map<String, Object>> internalError(Exception cause) {
        if (cause != null) {
            logUnexpected(cause, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "INTERNAL_ERROR"));
    }

    private void logUnexpected(Exception cause, HttpStatus status) {
        log.atError().setCause(cause).addKeyValue("code", "INTERNAL_ERROR").addKeyValue("status", status.value())
                .log("Error no controlado");
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> userNotFound() {
        return respond(HttpStatus.NOT_FOUND, "Usuario no encontrado", "NOT_FOUND");
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<Map<String, Object>> invalidField(FieldValidationException exception) {
        return validationFailed(List.of(fieldError(exception.field(), exception.code())));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> concurrentModification() {
        return respond(HttpStatus.CONFLICT, "El usuario ha cambiado mientras se editaba", "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> invalidParameters(HandlerMethodValidationException exception) {
        List<Map<String, String>> errors = exception.getParameterValidationResults().stream()
                .map(result -> fieldError(String.valueOf(result.getMethodParameter().getParameterName()),
                        "INVALID_FORMAT"))
                .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> invalidConstraints(ConstraintViolationException exception) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> fieldError(lastNode(violation.getPropertyPath().toString()), "INVALID_FORMAT"))
                .sorted(Comparator.comparing((Map<String, String> error) -> error.get("field")))
                .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidParameterType(MethodArgumentTypeMismatchException exception) {
        return validationFailed(List.of(fieldError(exception.getName(), "INVALID_FORMAT")));
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Autenticación requerida", "UNAUTHENTICATED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Acceso denegado", "FORBIDDEN");
    }

    public void writeForbidden(HttpServletResponse response, String title, String code) throws IOException {
        write(response, HttpStatus.FORBIDDEN, title, code);
    }

    private ResponseEntity<Map<String, Object>> validationFailed(List<Map<String, String>> errors) {
        Map<String, Object> body = body(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos", "VALIDATION_FAILED");
        body.put("errors", errors);
        logProblem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static Map<String, String> fieldError(String field, String code) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", field);
        error.put("code", code);
        return error;
    }

    private static String lastNode(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static String validationCode(FieldError error) {
        String constraint = error.getCode();
        if ("NotNull".equals(constraint)) {
            return "REQUIRED";
        }
        if ("Size".equals(constraint)) {
            return error.getRejectedValue() instanceof String value && value.isEmpty() ? "REQUIRED" : "TOO_LONG";
        }
        return "INVALID_FORMAT";
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus status, String title, String code) {
        logProblem(status, code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body(status, title, code));
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String code)
            throws IOException {
        logProblem(status, code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body(status, title, code));
    }

    private static void logProblem(HttpStatus status, String code) {
        log.atWarn().addKeyValue("code", code).addKeyValue("status", status.value())
                .log("Respuesta de error enviada");
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
