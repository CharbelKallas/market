package com.market.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response<T> {

    private Status status;
    private T payload;
    private Object errors;
    private Object metadata;

    private static <T> Response<T> of(Status status) {
        return new Response<T>().setStatus(status);
    }

    public static <T> Response<T> ok() {
        return of(Status.OK);
    }

    public static <T> Response<T> validationException() {
        return of(Status.VALIDATION_EXCEPTION);
    }

    public static <T> Response<T> wrongCredentials() {
        return of(Status.WRONG_CREDENTIALS);
    }

    public Response<T> addError(String message, Object details) {
        return setErrors(new ResponseError()
                .setMessage(message)
                .setDetails(details)
                .setTimestamp(new Date()));
    }

    public enum Status {
        OK, BAD_REQUEST, UNAUTHORIZED, VALIDATION_EXCEPTION, EXCEPTION, WRONG_CREDENTIALS, ACCESS_DENIED, NOT_FOUND, DUPLICATE_ENTITY, TOO_MANY_REQUESTS, CONFLICT
    }

}
