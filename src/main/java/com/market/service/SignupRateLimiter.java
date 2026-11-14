package com.market.service;

import com.market.exception.MarketException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits sign-ups per client address (fixed one-hour windows). Sign-up has to say when an email or username is
 * already taken, so this keeps that from being used to check large lists of addresses. In memory, per instance,
 * like {@link LoginAttemptService}.
 */
@Service
public class SignupRateLimiter {

    static final long WINDOW_MS = 60 * 60 * 1000L;

    private static final int MAX_ENTRIES = 100_000;

    private final int maxPerWindow;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public SignupRateLimiter(@Value("${app.signup.maxPerHourPerClient}") int maxPerWindow) {
        this.maxPerWindow = maxPerWindow;
    }

    /** Counts one sign-up attempt from this client, or throws 429 if the client has used up its hourly allowance. */
    public void acquire(String clientIp) {
        String client = LoginAttemptService.clientKey(clientIp);
        long now = System.currentTimeMillis();
        if (windows.size() >= MAX_ENTRIES && !windows.containsKey(client))
            windows.values().removeIf(window -> window.start < now - WINDOW_MS);

        Window window = windows.compute(client, (key, current) -> {
            Window next = current == null || current.start < now - WINDOW_MS ? new Window(now) : current;
            next.count++;
            return next;
        });
        if (window.count > maxPerWindow)
            throw MarketException.tooManyRequests("Too many sign-ups from this address. Please try again later.",
                    (window.start + WINDOW_MS - now + 999) / 1000);
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}
