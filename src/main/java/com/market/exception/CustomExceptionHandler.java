package com.market.exception;

import com.market.payload.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class CustomExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomExceptionHandler.class);

    @ExceptionHandler(MarketException.class)
    public ResponseEntity<Object> handleMarketException(MarketException ex) {
        if (ex.getHttpStatus().is5xxServerError())
            log.error(ex.getMessage(), ex);
        Response<Object> body = new Response<>().setStatus(ex.getStatus()).addError(ex.getMessage(), null);
        return new ResponseEntity<>(body, ex.getHttpStatus());
    }

    /** Unique-constraint races that slip past the service's exists checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        Response<Object> body = new Response<>().setStatus(Response.Status.DUPLICATE_ENTITY)
                .addError("The record conflicts with existing data.", null);
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        String message = ex instanceof DisabledException
                ? "Account is not verified yet"
                : "Wrong username or password";
        return new ResponseEntity<>(Response.wrongCredentials().addError(message, null), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            fields.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
        }
        Response<Object> body = Response.validationException().addError("Validation failed", fields);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatus status,
                                                                  @NonNull WebRequest request) {
        return validationError(ex.getBindingResult());
    }

    @Override
    protected ResponseEntity<Object> handleBindException(@NonNull BindException ex,
                                                         @NonNull HttpHeaders headers,
                                                         @NonNull HttpStatus status,
                                                         @NonNull WebRequest request) {
        return validationError(ex.getBindingResult());
    }

    private ResponseEntity<Object> validationError(BindingResult result) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : result.getFieldErrors())
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        Response<Object> body = Response.validationException().addError("Validation failed", fields);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

}
