package com.market.config;

import com.market.service.ClientAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Warns if the app looks like it is behind a proxy it doesn't trust: a request still carries X-Forwarded-For
 * (a trusted proxy's header would have been applied) and comes from a private-network address. Then every user
 * appears to come from the proxy, so the per-address sign-in and sign-up limits act site-wide. The fix is to set
 * TRUSTED_PROXIES to match the proxy. The warning repeats at most once an hour while the problem lasts, so it doesn't
 * flood the log but isn't lost to log rotation either.
 */
@Component
public class UntrustedProxyDetector extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UntrustedProxyDetector.class);

    static final long WARN_INTERVAL_MS = 60 * 60 * 1000L;

    private final long warnIntervalMs;

    /** When the last warning was logged; 0 = never. */
    private final AtomicLong lastWarnedAt = new AtomicLong();

    private final AtomicInteger warnings = new AtomicInteger();

    public UntrustedProxyDetector() {
        this(WARN_INTERVAL_MS);
    }

    public UntrustedProxyDetector(long warnIntervalMs) {
        this.warnIntervalMs = warnIntervalMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        long last = lastWarnedAt.get();
        if ((last == 0 || now - last >= warnIntervalMs)
                && StringUtils.hasText(request.getHeader("X-Forwarded-For"))
                && ClientAddresses.isPrivateAddress(request.getRemoteAddr())
                && lastWarnedAt.compareAndSet(last, now)) {
            warnings.incrementAndGet();
            log.warn("Requests arrive through a proxy at {} that is not trusted, so all clients share its address and "
                    + "the per-address sign-in and sign-up limits apply site-wide. Set TRUSTED_PROXIES to a regex "
                    + "matching the proxy's addresses.", request.getRemoteAddr());
        }
        chain.doFilter(request, response);
    }

    /** How many times the warning has been logged. */
    public int warningCount() {
        return warnings.get();
    }
}
