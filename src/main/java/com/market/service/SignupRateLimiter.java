package com.market.service;

import com.market.exception.MarketException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limits how many sign-ups per client address (per one-hour window) may hit an email or username that is already
 * taken. Sign-up has to say when that happens, so this stops it being used to check large lists of addresses.
 * Ordinary sign-ups are never counted, so many users sharing one address (an office, or a proxy whose address isn't
 * in TRUSTED_PROXIES) can still all register. In memory, per instance, like {@link LoginAttemptService}.
 */
@Service
public class SignupRateLimiter {

    static final long WINDOW_MS = 60 * 60 * 1000L;

    static final int MAX_ENTRIES = 100_000;

    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final int maxConflictsPerWindow;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private final AtomicLong lastCleanup = new AtomicLong();

    public SignupRateLimiter(@Value("${app.signup.maxConflictsPerHourPerClient}") int maxConflictsPerWindow) {
        this.maxConflictsPerWindow = maxConflictsPerWindow;
    }

    /** Throws 429 if this client has already hit too many taken emails/usernames in the current window. */
    public void checkNotBlocked(String clientIp) {
        long now = System.currentTimeMillis();
        Window window = windows.get(LoginAttemptService.clientKey(clientIp));
        if (window != null && window.start >= now - WINDOW_MS && window.conflicts >= maxConflictsPerWindow)
            throw MarketException.tooManyRequests("Too many sign-up attempts from this address. Please try again later.",
                    (window.start + WINDOW_MS - now + 999) / 1000);
    }

    /** Records a sign-up that hit an email or username that is already taken. */
    public void recordConflict(String clientIp) {
        String client = LoginAttemptService.clientKey(clientIp);
        long now = System.currentTimeMillis();
        if (!windows.containsKey(client) && windows.size() >= MAX_ENTRIES) {
            long last = lastCleanup.get();
            if (now - last >= CLEANUP_INTERVAL_MS && lastCleanup.compareAndSet(last, now))
                windows.values().removeIf(window -> window.start < now - WINDOW_MS);
            if (windows.size() >= MAX_ENTRIES)
                return; // Full of live entries: don't grow further.
        }
        windows.compute(client, (key, current) -> {
            Window next = current == null || current.start < now - WINDOW_MS ? new Window(now) : current;
            next.conflicts++;
            return next;
        });
    }

    private static final class Window {
        final long start;
        int conflicts;

        Window(long start) {
            this.start = start;
        }
    }
}
