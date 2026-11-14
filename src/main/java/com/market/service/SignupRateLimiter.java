package com.market.service;

import com.market.exception.MarketException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/**
 * Limits sign-ups per client address, in one-hour windows:
 * <ul>
 *   <li>a generous cap on all sign-ups (each one sends a code to the given email/phone), against mass account
 *   creation and using sign-up to send codes to arbitrary addresses;</li>
 *   <li>a strict cap on sign-ups that hit an email or username that is already taken, since sign-up has to say so,
 *   against checking lists of addresses. Sign-ups still in progress count against it too, so a burst of parallel
 *   requests can't all get an answer before the first conflict is recorded.</li>
 * </ul>
 * Use {@link #begin} before a sign-up and {@link #finish} after it. In memory, per instance, like
 * {@link LoginAttemptService}.
 */
@Service
public class SignupRateLimiter {

    static final long WINDOW_MS = 60 * 60 * 1000L;

    /** When full, the least recently used window is dropped (see {@link BoundedLruMap}), so nobody is refused. */
    static final int MAX_ENTRIES = 100_000;

    private final int maxSignupsPerWindow;

    private final int maxConflictsPerWindow;

    private final BoundedLruMap<Window> windows = new BoundedLruMap<>(MAX_ENTRIES);

    public SignupRateLimiter(@Value("${app.signup.maxPerHourPerClient}") int maxSignupsPerWindow,
                             @Value("${app.signup.maxConflictsPerHourPerClient}") int maxConflictsPerWindow) {
        this.maxSignupsPerWindow = maxSignupsPerWindow;
        this.maxConflictsPerWindow = maxConflictsPerWindow;
    }

    /** Reserves a sign-up for this client, or throws 429 if a limit is reached. Always pair with {@link #finish}. */
    public void begin(String clientIp) {
        String client = ClientAddresses.clientKey(clientIp);
        long now = System.currentTimeMillis();
        long[] retryAt = {0};
        windows.compute(client, (key, current) -> {
            Window window = current == null ? new Window(now) : current;
            if (window.start < now - WINDOW_MS)
                window.restart(now);
            if (window.signups >= maxSignupsPerWindow || window.conflicts + window.inFlight >= maxConflictsPerWindow) {
                retryAt[0] = window.start + WINDOW_MS;
                return window;
            }
            window.signups++;
            window.inFlight++;
            return window;
        });
        if (retryAt[0] > 0)
            throw MarketException.tooManyRequests("Too many sign-ups from this address. Please try again later.",
                    Math.max(1, (retryAt[0] - now + 999) / 1000));
    }

    /** Ends a sign-up reserved with {@link #begin}; conflict = it hit an email or username already taken. */
    public void finish(String clientIp, boolean conflict) {
        windows.computeIfPresent(ClientAddresses.clientKey(clientIp), (key, window) -> {
            window.inFlight = Math.max(0, window.inFlight - 1);
            if (conflict)
                window.conflicts++;
            return window;
        });
    }

    private static final class Window {
        long start;
        int signups;
        int conflicts;
        /** Sign-ups started and not finished yet; kept across window restarts. */
        int inFlight;

        Window(long start) {
            this.start = start;
        }

        void restart(long now) {
            start = now;
            signups = 0;
            conflicts = 0;
        }
    }
}
