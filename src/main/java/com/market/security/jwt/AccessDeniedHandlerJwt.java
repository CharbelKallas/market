package com.market.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.payload.Response;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes 403 responses in the same JSON shape as every other error. */
@Component
public class AccessDeniedHandlerJwt implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public AccessDeniedHandlerJwt(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Response<Object> body = Response.accessDenied().addError("Access denied", null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
