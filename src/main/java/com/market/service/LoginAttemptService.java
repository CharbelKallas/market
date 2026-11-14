package com.market.service;

import com.market.exception.MarketException;
import com.market.payload.Response;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles password guessing, in memory (per application instance, reset on restart):
 * <ul>
 *   <li>5 wrong passwords for the same username from the same client block that pair for 15 minutes. Keying on the
 *   client too means an attacker can't lock the real user out from another network.</li>
 *   <li>50 wrong passwords from the same client, across any usernames, block that client for 15 minutes, so trying
 *   many usernames (or flooding the tracker) from one place is limited too.</li>
 * </ul>
 * An IPv6 client is identified by its /64 network, since one host usually controls that whole range.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;

    static final int MAX_ATTEMPTS_PER_CLIENT = 50;

    static final long LOCK_MS = 15 * 60 * 1000L;

    /** Upper bound per map, so a flood of random usernames or addresses can't exhaust memory. */
    static final int MAX_ENTRIES = 100_000;

    private final Map<String, Attempts> pairs = new ConcurrentHashMap<>();

    private final Map<String, Attempts> clients = new ConcurrentHashMap<>();

    public void checkNotBlocked(String username, String clientIp) {
        String client = clientKey(clientIp);
        long now = System.currentTimeMillis();
        long blockedUntil = Math.max(blockedUntil(pairs.get(username + '\n' + client), now),
                blockedUntil(clients.get(client), now));
        if (blockedUntil > now)
            throw new MarketException("Too many failed sign-ins. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS, Response.Status.TOO_MANY_REQUESTS)
                    .setRetryAfterSeconds((blockedUntil - now + 999) / 1000);
    }

    public void recordFailure(String username, String clientIp) {
        String client = clientKey(clientIp);
        long now = System.currentTimeMillis();
        record(clients, client, MAX_ATTEMPTS_PER_CLIENT, now);
        record(pairs, username + '\n' + client, MAX_ATTEMPTS, now);
    }

    public void recordSuccess(String username, String clientIp) {
        pairs.remove(username + '\n' + clientKey(clientIp));
    }

    private static void record(Map<String, Attempts> map, String key, int maxAttempts, long now) {
        if (!map.containsKey(key) && map.size() >= MAX_ENTRIES) {
            map.values().removeIf(entry -> entry.lastFailure < now - LOCK_MS && entry.blockedUntil < now);
            // Still full of live entries: don't grow further. The per-client limit still applies to the sender.
            if (map.size() >= MAX_ENTRIES)
                return;
        }
        map.compute(key, (k, entry) -> {
            Attempts next = entry == null || entry.lastFailure < now - LOCK_MS ? new Attempts() : entry;
            next.blockedUntil = entry == null ? 0 : entry.blockedUntil;
            next.lastFailure = now;
            if (++next.failures >= maxAttempts) {
                next.blockedUntil = now + LOCK_MS;
                next.failures = 0;
            }
            return next;
        });
    }

    private static long blockedUntil(Attempts entry, long now) {
        return entry == null ? 0 : entry.blockedUntil;
    }

    /** The full address for IPv4; the /64 network for IPv6. */
    static String clientKey(String clientIp) {
        try {
            InetAddress address = InetAddress.getByName(clientIp);
            if (address instanceof Inet6Address) {
                byte[] bytes = address.getAddress();
                StringBuilder prefix = new StringBuilder();
                for (int i = 0; i < 8; i++)
                    prefix.append(String.format("%02x", bytes[i]));
                return prefix + "::/64";
            }
            return address.getHostAddress();
        } catch (Exception e) {
            return String.valueOf(clientIp);
        }
    }

    private static final class Attempts {
        int failures;
        long lastFailure;
        long blockedUntil;
    }
}
