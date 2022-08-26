package com.market.exception;

public class MarketException extends RuntimeException {

    private MarketException(String message) {
        super(message);
    }

    private MarketException(String message, Throwable cause) {
        super(message, cause);
    }

    private MarketException(Throwable cause) {
        super(cause);
    }

    private MarketException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    private MarketException() {
    }

    public static RuntimeException throwException(String message) {
        return new MarketException(message);
    }

    public static RuntimeException throwException(String message, Throwable cause) {
        return new MarketException(message, cause);
    }

    public static RuntimeException throwException(Throwable cause) {
        return new MarketException(cause);
    }

    public static RuntimeException throwException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        return new MarketException(message, cause, enableSuppression, writableStackTrace);
    }

    public static RuntimeException throwException() {
        return new MarketException();
    }
}
