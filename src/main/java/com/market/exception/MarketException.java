package com.market.exception;

import com.market.payload.Response;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A business error that is reported to the client with a specific HTTP status.
 */
@Getter
public class MarketException extends RuntimeException {

    private final HttpStatus httpStatus;

    private final Response.Status status;

    /** For 429/503 responses: how long the client should wait, sent as the Retry-After header; otherwise null. */
    private final Long retryAfterSeconds;

    public MarketException(String message, HttpStatus httpStatus, Response.Status status) {
        this(message, httpStatus, status, null);
    }

    public MarketException(String message, HttpStatus httpStatus, Response.Status status, Throwable cause) {
        this(message, httpStatus, status, cause, null);
    }

    private MarketException(String message, HttpStatus httpStatus, Response.Status status, Throwable cause,
                            Long retryAfterSeconds) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static MarketException tooManyRequests(String message, long retryAfterSeconds) {
        return new MarketException(message, HttpStatus.TOO_MANY_REQUESTS, Response.Status.TOO_MANY_REQUESTS, null,
                retryAfterSeconds);
    }

    public static MarketException serviceUnavailable(String message, long retryAfterSeconds) {
        return new MarketException(message, HttpStatus.SERVICE_UNAVAILABLE, Response.Status.EXCEPTION, null,
                retryAfterSeconds);
    }

    public static MarketException badRequest(String message) {
        return new MarketException(message, HttpStatus.BAD_REQUEST, Response.Status.BAD_REQUEST);
    }

    public static MarketException notFound(String message) {
        return new MarketException(message, HttpStatus.NOT_FOUND, Response.Status.NOT_FOUND);
    }

    public static MarketException duplicate(String message) {
        return new MarketException(message, HttpStatus.CONFLICT, Response.Status.DUPLICATE_ENTITY);
    }

    public static MarketException internal(String message, Throwable cause) {
        return new MarketException(message, HttpStatus.INTERNAL_SERVER_ERROR, Response.Status.EXCEPTION, cause);
    }
}
