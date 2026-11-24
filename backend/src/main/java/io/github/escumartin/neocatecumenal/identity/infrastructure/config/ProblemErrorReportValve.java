package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ProblemDetailsHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class ProblemErrorReportValve extends ErrorReportValve {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        HttpStatus status = HttpStatus.resolve(response.getStatus());
        if (status == null || status.value() < 400 || response.getContentWritten() > 0) {
            return;
        }
        try {
            byte[] body = MAPPER.writeValueAsBytes(ProblemDetailsHandler.problemFor(status));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el problema", e);
        } catch (IOException e) {
            return;
        }
    }
}
