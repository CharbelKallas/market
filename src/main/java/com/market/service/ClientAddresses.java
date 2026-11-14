package com.market.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

/** Client IP address helpers shared by the rate limiters and the proxy check. They never do a DNS lookup. */
public final class ClientAddresses {

    /** Four dot-separated numbers from 0 to 255: anything else could make InetAddress fall back to a DNS lookup. */
    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    private ClientAddresses() {
    }

    /**
     * The key a client is counted under: the full address for IPv4, the /64 network for IPv6 (one host usually
     * controls that whole range). Anything that isn't a numeric literal is used as given.
     */
    public static String clientKey(String clientIp) {
        if (clientIp == null)
            return "unknown";
        InetAddress address = parseLiteral(clientIp);
        if (address == null)
            return clientIp;
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 8; i++)
                prefix.append(Character.forDigit((bytes[i] >> 4) & 0xf, 16))
                        .append(Character.forDigit(bytes[i] & 0xf, 16));
            return prefix + "::/64";
        }
        return address.getHostAddress();
    }

    /**
     * Parses a numeric IPv4/IPv6 literal, or returns null for anything else. An IPv6 zone ID (fe80::1%eth0, as the
     * servlet container reports link-local peers) is dropped: it names a local interface, not part of the address.
     */
    public static InetAddress parseLiteral(String text) {
        if (text == null)
            return null;
        int zone = text.indexOf('%');
        if (zone >= 0 && text.indexOf(':') >= 0)
            text = text.substring(0, zone);
        boolean ipv4 = IPV4.matcher(text).matches();
        boolean ipv6 = !ipv4 && text.indexOf(':') >= 0 && IPV6.matcher(text).matches();
        if (!ipv4 && !ipv6)
            return null;
        try {
            // A valid numeric literal: InetAddress parses it without any lookup.
            return InetAddress.getByName(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A non-public address a proxy or load balancer typically has: IPv4 private (10/8, 172.16/12, 192.168/16),
     * carrier-grade NAT / shared (100.64/10), link-local (169.254/16, fe80::/10), IPv6 unique-local (fc00::/7) or
     * loopback (a proxy on the same host, when TRUSTED_PROXIES has been changed not to cover it).
     */
    public static boolean isPrivateAddress(String address) {
        InetAddress parsed = parseLiteral(address);
        if (parsed == null)
            return false;
        if (parsed.isSiteLocalAddress() || parsed.isLinkLocalAddress() || parsed.isLoopbackAddress())
            return true;
        byte[] bytes = parsed.getAddress();
        if (parsed instanceof Inet6Address)
            return (bytes[0] & 0xfe) == 0xfc;
        return (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64;
    }
}
