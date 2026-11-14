package com.market.service;

import com.market.exception.MarketException;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Throttles password guessing:
 * <ul>
 *   <li>5 wrong passwords for the same username from the same client block that pair for 15 minutes. Keying on the
 *   client too means an attacker can't lock the real user out from another network.</li>
 *   <li>500 wrong passwords from one client, across any usernames, block that client for 15 minutes, which stops
 *   username spraying. Users who share the client's IP (an office, a carrier NAT) and have signed in successfully
 *   from it before are not affected; their own per-username limit still applies.</li>
 * </ul>
 * An IPv6 client is identified by its /64 network, since one host usually controls that whole range.
 * <p>
 * State is in memory: it is per application instance and resets on restart. Run a single instance, or move these
 * counters to a shared store (database or Redis) before scaling out.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;

    static final int MAX_ATTEMPTS_PER_CLIENT = 500;

    static final long LOCK_MS = 15 * 60 * 1000L;

    /** How long a successful sign-in keeps a (username, client) pair trusted through a client-wide block. */
    static final long TRUSTED_MS = 30L * 24 * 60 * 60 * 1000;

    /** Upper bound per map, so a flood of random usernames or addresses can't exhaust memory. */
    static final int MAX_ENTRIES = 100_000;

    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final Tracker<Attempts> pairs = new Tracker<>();

    private final Tracker<Attempts> clients = new Tracker<>();

    /** Pair key to the time of its last successful sign-in. */
    private final Tracker<Long> trusted = new Tracker<>();

    public void checkNotBlocked(String username, String clientIp) {
        String client = clientKey(clientIp);
        String pair = username + '\n' + client;
        long now = System.currentTimeMillis();

        long blockedUntil = blockedUntil(pairs.map.get(pair));
        Long trustedAt = trusted.map.get(pair);
        if (trustedAt == null || trustedAt < now - TRUSTED_MS)
            blockedUntil = Math.max(blockedUntil, blockedUntil(clients.map.get(client)));

        if (blockedUntil > now)
            throw MarketException.tooManyRequests("Too many failed sign-ins. Please try again later.",
                    (blockedUntil - now + 999) / 1000);
    }

    public void recordFailure(String username, String clientIp) {
        String client = clientKey(clientIp);
        long now = System.currentTimeMillis();
        recordFailure(clients, client, MAX_ATTEMPTS_PER_CLIENT, now);
        recordFailure(pairs, username + '\n' + client, MAX_ATTEMPTS, now);
    }

    public void recordSuccess(String username, String clientIp) {
        String pair = username + '\n' + clientKey(clientIp);
        long now = System.currentTimeMillis();
        pairs.map.remove(pair);
        if (trusted.hasRoomFor(pair, now, at -> at < now - TRUSTED_MS))
            trusted.map.put(pair, now);
    }

    private static void recordFailure(Tracker<Attempts> tracker, String key, int maxAttempts, long now) {
        if (!tracker.hasRoomFor(key, now, entry -> entry.lastFailure < now - LOCK_MS && entry.blockedUntil < now))
            return; // Full of live entries: don't grow further.
        tracker.map.compute(key, (k, entry) -> {
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

    private static long blockedUntil(Attempts entry) {
        return entry == null ? 0 : entry.blockedUntil;
    }

    /**
     * The full address for IPv4; the /64 network for IPv6. Only numeric literals are parsed (never a DNS lookup);
     * anything else is used as given.
     */
    public static String clientKey(String clientIp) {
        if (clientIp == null)
            return "unknown";
        boolean ipv4 = IPV4.matcher(clientIp).matches();
        boolean ipv6 = !ipv4 && clientIp.indexOf(':') >= 0 && IPV6.matcher(clientIp).matches();
        if (!ipv4 && !ipv6)
            return clientIp;
        try {
            // A numeric literal: InetAddress parses it without any lookup.
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
            return clientIp;
        }
    }

    /** A bounded map whose stale entries are swept at most once a minute, never on every request. */
    private static final class Tracker<V> {
        final Map<String, V> map = new ConcurrentHashMap<>();
        final AtomicLong lastCleanup = new AtomicLong();

        boolean hasRoomFor(String key, long now, Predicate<V> stale) {
            if (map.size() < MAX_ENTRIES || map.containsKey(key))
                return true;
            long last = lastCleanup.get();
            if (now - last >= CLEANUP_INTERVAL_MS && lastCleanup.compareAndSet(last, now))
                map.values().removeIf(stale);
            return map.size() < MAX_ENTRIES;
        }
    }

    private static final class Attempts {
        int failures;
        long lastFailure;
        long blockedUntil;
    }
}
