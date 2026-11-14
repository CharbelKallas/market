package com.market.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.payload.Response;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes security-layer errors (401/403) in the same JSON envelope the controllers use. */
@Component
public class JsonErrorWriter {

    private final ObjectMapper objectMapper;

    public JsonErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int httpStatus, Response.Status status, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new Response<>().setStatus(status).addError(message, null));
    }
}
