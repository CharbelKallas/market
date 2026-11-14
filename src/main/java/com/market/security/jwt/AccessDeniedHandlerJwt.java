package com.market.security.jwt;

import com.market.payload.Response;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes 403 responses in the same JSON shape as every other error. */
@Component
public class AccessDeniedHandlerJwt implements AccessDeniedHandler {

    private final JsonErrorWriter errorWriter;

    public AccessDeniedHandlerJwt(JsonErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, Response.Status.ACCESS_DENIED, "Access denied");
    }
}
