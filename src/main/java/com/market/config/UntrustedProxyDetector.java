package com.market.config;

import com.market.service.LoginAttemptService;
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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Warns once if the app looks like it is behind a proxy it doesn't trust: a request still carries X-Forwarded-For
 * (a trusted proxy's header would have been applied) and comes from a private-network address. Then every user
 * appears to come from the proxy, so the per-address sign-in and sign-up limits act site-wide. The fix is to set
 * TRUSTED_PROXIES to match the proxy.
 */
@Component
public class UntrustedProxyDetector extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UntrustedProxyDetector.class);

    private final AtomicBoolean warned = new AtomicBoolean();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!warned.get() && StringUtils.hasText(request.getHeader("X-Forwarded-For"))
                && isPrivateAddress(request.getRemoteAddr()) && warned.compareAndSet(false, true))
            log.warn("Requests arrive through a proxy at {} that is not trusted, so all clients share its address and "
                    + "the per-address sign-in and sign-up limits apply site-wide. Set TRUSTED_PROXIES to a regex "
                    + "matching the proxy's addresses.", request.getRemoteAddr());
        chain.doFilter(request, response);
    }

    public boolean hasWarned() {
        return warned.get();
    }

    /** IPv4 private ranges (10/8, 172.16/12, 192.168/16) or IPv6 unique-local (fc00::/7). Never resolves names. */
    public static boolean isPrivateAddress(String address) {
        InetAddress parsed = LoginAttemptService.parseLiteral(address);
        if (parsed == null)
            return false;
        if (parsed instanceof Inet6Address)
            return (parsed.getAddress()[0] & 0xfe) == 0xfc;
        return parsed.isSiteLocalAddress();
    }
}
