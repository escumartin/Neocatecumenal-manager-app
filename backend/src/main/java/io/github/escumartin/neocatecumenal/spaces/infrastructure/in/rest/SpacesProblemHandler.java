package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingAlreadyStarted;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotActive;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotPending;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomHasActiveBookings;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomConcurrentlyModified;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = SpacesProblemHandler.class)
public class SpacesProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(SpacesProblemHandler.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @ExceptionHandler(BookingRequestNotPending.class)
    public ResponseEntity<Map<String, Object>> bookingRequestNotPending(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "La solicitud ya está resuelta", "BOOKING_REQUEST_ALREADY_RESOLVED");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> concurrentModification(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "La solicitud ya está resuelta", "BOOKING_REQUEST_ALREADY_RESOLVED");
    }

    @ExceptionHandler(RoomConcurrentlyModified.class)
    public ResponseEntity<Map<String, Object>> roomConcurrentlyModified(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "El salón ha cambiado mientras se editaba",
                "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(BookingRequestNotActive.class)
    public ResponseEntity<Map<String, Object>> bookingRequestNotActive(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "La solicitud no está activa", "BOOKING_REQUEST_NOT_ACTIVE");
    }

    @ExceptionHandler(BookingAlreadyStarted.class)
    public ResponseEntity<Map<String, Object>> bookingAlreadyStarted(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "La reserva ya ha empezado", "BOOKING_ALREADY_STARTED");
    }

    @ExceptionHandler(RoomNameTaken.class)
    public ResponseEntity<Map<String, Object>> roomNameTaken(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "El nombre del salón ya existe", "ROOM_NAME_TAKEN");
    }

    @ExceptionHandler(RoomInactive.class)
    public ResponseEntity<Map<String, Object>> roomInactive(Exception exception) {
        return respond(exception, HttpStatus.CONFLICT, "El salón está dado de baja", "ROOM_INACTIVE");
    }

    @ExceptionHandler(RoomHasActiveBookings.class)
    public ResponseEntity<Map<String, Object>> roomHasActiveBookings(RoomHasActiveBookings exception) {
        Map<String, Object> body = body(HttpStatus.CONFLICT, "El salón tiene reservas activas",
                "ROOM_HAS_ACTIVE_BOOKINGS");
        body.put("activeBookings", exception.count());
        return send(exception, HttpStatus.CONFLICT, "ROOM_HAS_ACTIVE_BOOKINGS", body);
    }

    @ExceptionHandler({RoomNotFound.class, BookingRequestNotFound.class})
    public ResponseEntity<Map<String, Object>> notFound(Exception exception) {
        return respond(exception, HttpStatus.NOT_FOUND, "Recurso no encontrado", "NOT_FOUND");
    }

    @ExceptionHandler(SpacesForbidden.class)
    public ResponseEntity<Map<String, Object>> forbidden(Exception exception) {
        return respond(exception, HttpStatus.FORBIDDEN, "Acceso denegado", "FORBIDDEN");
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> unauthenticated(Exception exception) {
        return respond(exception, HttpStatus.UNAUTHORIZED, "Autenticación requerida", "UNAUTHENTICATED");
    }

    @ExceptionHandler(SpacesFieldInvalid.class)
    public ResponseEntity<Map<String, Object>> invalidField(SpacesFieldInvalid exception) {
        Map<String, Object> body = body(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos", "VALIDATION_FAILED");
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", exception.field());
        error.put("code", exception.code());
        body.put("errors", List.of(error));
        return send(exception, HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException exception) {
        Map<String, Object> body = body(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos", "VALIDATION_FAILED");
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", exception.getParameterName());
        error.put("code", "REQUIRED");
        body.put("errors", List.of(error));
        return send(exception, HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", body);
    }

    @ExceptionHandler(RoomOccupied.class)
    public ResponseEntity<Map<String, Object>> roomOccupied(RoomOccupied exception) {
        Map<String, Object> body = body(HttpStatus.CONFLICT, "El salón está ocupado", "ROOM_OCCUPIED");
        body.put("conflicts", exception.conflicts().stream().map(conflict -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", conflict.date().toString());
            item.put("startTime", time(conflict.slot().start()));
            item.put("endTime", time(conflict.slot().end()));
            return item;
        }).toList());
        return send(exception, HttpStatus.CONFLICT, "ROOM_OCCUPIED", body);
    }

    private static String time(LocalTime time) {
        return TIME.format(time);
    }

    private ResponseEntity<Map<String, Object>> respond(Exception exception, HttpStatus status, String title,
            String code) {
        return send(exception, status, code, body(status, title, code));
    }

    private ResponseEntity<Map<String, Object>> send(Exception exception, HttpStatus status, String code,
            Map<String, Object> body) {
        var entry = log.atWarn().addKeyValue("code", code).addKeyValue("status", status.value())
                .addKeyValue("exception", exception.getClass().getSimpleName());
        if (exception instanceof SpacesFieldInvalid invalid) {
            entry = entry.addKeyValue("field", invalid.field());
        }
        entry.log("Respuesta de error enviada");
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
