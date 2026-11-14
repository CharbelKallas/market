package com.market.service;

import com.market.exception.MarketException;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    static final long TRUSTED_MS = 7L * 24 * 60 * 60 * 1000;

    /** Upper bound per map, so a flood of random usernames or addresses can't exhaust memory. */
    static final int MAX_ENTRIES = 100_000;

    private static final long CLEANUP_INTERVAL_MS = 60_000;

    /** Four dot-separated numbers from 0 to 255: anything else could make InetAddress fall back to a DNS lookup. */
    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final Tracker<Attempts> pairs = new Tracker<>();

    private final Tracker<Attempts> clients = new Tracker<>();

    /**
     * Pair key to the time of its last successful sign-in, least recently used first. When full, the pair unused for
     * the longest time is dropped, so recently active users keep their trust even if someone churns the map.
     */
    private final Map<String, Long> trusted = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /**
     * Call before checking the password. Throws 429 if blocked; otherwise counts the attempt for the (username, client)
     * pair right away, so a burst of parallel guesses can't all pass before the first failure is recorded: the 6th
     * concurrent attempt is already refused. {@link #recordSuccess} gives the attempt back.
     */
    public void beginAttempt(String username, String clientIp) {
        String client = clientKey(clientIp);
        String pair = username + '\n' + client;
        long now = System.currentTimeMillis();

        Long trustedAt = trusted.get(pair);
        if (trustedAt == null || trustedAt < now - TRUSTED_MS)
            rejectIfBlocked(blockedUntil(clients.map.get(client)), now);

        if (!pairs.hasRoomFor(pair, now, entry -> entry.lastFailure < now - LOCK_MS && entry.blockedUntil < now)) {
            // Full of live entries: can't track this pair, but still honour a block it already has.
            rejectIfBlocked(blockedUntil(pairs.map.get(pair)), now);
            return;
        }
        long[] blockedUntil = {0};
        pairs.map.compute(pair, (key, entry) -> {
            if (entry != null && entry.blockedUntil > now) {
                blockedUntil[0] = entry.blockedUntil;
                return entry;
            }
            return count(entry, MAX_ATTEMPTS, now);
        });
        rejectIfBlocked(blockedUntil[0], now);
    }

    /** A wrong password: also counts toward the client-wide limit (the pair was counted in beginAttempt). */
    public void recordFailure(String username, String clientIp) {
        String client = clientKey(clientIp);
        long now = System.currentTimeMillis();
        if (clients.hasRoomFor(client, now, entry -> entry.lastFailure < now - LOCK_MS && entry.blockedUntil < now))
            clients.map.compute(client, (key, entry) -> count(entry, MAX_ATTEMPTS_PER_CLIENT, now));
    }

    public void recordSuccess(String username, String clientIp) {
        String pair = username + '\n' + clientKey(clientIp);
        long now = System.currentTimeMillis();
        pairs.map.remove(pair);
        trusted.put(pair, now);
    }

    /** Adds one attempt to a counter (inside compute, so atomically); reaching the limit starts a 15-minute block. */
    private static Attempts count(Attempts entry, int maxAttempts, long now) {
        Attempts next = entry == null || entry.lastFailure < now - LOCK_MS ? new Attempts() : entry;
        next.blockedUntil = entry == null ? 0 : entry.blockedUntil;
        next.lastFailure = now;
        if (++next.failures >= maxAttempts) {
            next.blockedUntil = now + LOCK_MS;
            next.failures = 0;
        }
        return next;
    }

    private static void rejectIfBlocked(long blockedUntil, long now) {
        if (blockedUntil > now)
            throw MarketException.tooManyRequests("Too many failed sign-ins. Please try again later.",
                    (blockedUntil - now + 999) / 1000);
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
