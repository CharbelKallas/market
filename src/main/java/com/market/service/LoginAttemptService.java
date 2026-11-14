package com.market.service;

import com.market.exception.MarketException;
import com.market.payload.Response;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles password guessing per (username, client IP): 5 wrong passwords in a row block that pair for 15 minutes.
 * Keying on the IP too means an attacker can't lock the real user out from another network.
 * In memory, so it is per application instance and resets on restart.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;

    static final long LOCK_MS = 15 * 60 * 1000L;

    /** Upper bound on tracked pairs, so a flood of random usernames can't exhaust memory. */
    private static final int MAX_ENTRIES = 100_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void checkNotBlocked(String username, String clientIp) {
        Attempts entry = attempts.get(key(username, clientIp));
        if (entry != null && entry.blockedUntil > System.currentTimeMillis())
            throw new MarketException("Too many failed sign-ins. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS, Response.Status.TOO_MANY_REQUESTS);
    }

    public void recordFailure(String username, String clientIp) {
        long now = System.currentTimeMillis();
        if (attempts.size() >= MAX_ENTRIES)
            attempts.values().removeIf(entry -> entry.lastFailure < now - LOCK_MS);
        attempts.compute(key(username, clientIp), (key, entry) -> {
            Attempts next = entry == null || entry.lastFailure < now - LOCK_MS ? new Attempts() : entry;
            next.lastFailure = now;
            if (++next.failures >= MAX_ATTEMPTS) {
                next.blockedUntil = now + LOCK_MS;
                next.failures = 0;
            }
            return next;
        });
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    private static String key(String username, String clientIp) {
        return username + '\n' + clientIp;
    }

    private static final class Attempts {
        int failures;
        long lastFailure;
        long blockedUntil;
    }
}
