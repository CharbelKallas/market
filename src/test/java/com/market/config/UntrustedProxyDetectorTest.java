package com.market.config;

import com.market.service.ClientAddresses;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class UntrustedProxyDetectorTest {

    @Test
    void recognisesPrivateAddresses() {
        assertThat(ClientAddresses.isPrivateAddress("10.42.0.7")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("fd12:3456::1")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("100.64.0.1")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("100.127.255.254")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("169.254.1.1")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("fe80::1")).isTrue();
        // Tomcat reports link-local IPv6 peers with a zone ID.
        assertThat(ClientAddresses.isPrivateAddress("fe80:0:0:0:0:0:0:1%2")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("fe80::1%eth0")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("127.0.0.1")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(ClientAddresses.isPrivateAddress("100.128.0.1")).isFalse();
        assertThat(ClientAddresses.isPrivateAddress("100.63.255.255")).isFalse();
        assertThat(ClientAddresses.isPrivateAddress("203.0.113.9")).isFalse();
        assertThat(ClientAddresses.isPrivateAddress("999.1.1.1")).isFalse();
        assertThat(ClientAddresses.isPrivateAddress("host%name")).isFalse();
    }

    @Test
    void clientKeyIgnoresZoneId() {
        assertThat(ClientAddresses.clientKey("fe80::1%eth0")).isEqualTo("fe80000000000000::/64");
        assertThat(ClientAddresses.clientKey("2001:db8:1:2::7")).isEqualTo("20010db800010002::/64");
    }

    @Test
    void warnsOncePerInterval() throws Exception {
        UntrustedProxyDetector detector = new UntrustedProxyDetector();
        detector.doFilter(forwarded("203.0.113.9"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(detector.warningCount()).isZero();

        detector.doFilter(forwarded("10.42.0.7"), new MockHttpServletResponse(), new MockFilterChain());
        detector.doFilter(forwarded("10.42.0.7"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(detector.warningCount()).isEqualTo(1);

        // With no interval, every request from the untrusted proxy is reported again.
        UntrustedProxyDetector repeating = new UntrustedProxyDetector(0);
        repeating.doFilter(forwarded("100.64.0.1"), new MockHttpServletResponse(), new MockFilterChain());
        repeating.doFilter(forwarded("100.64.0.1"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(repeating.warningCount()).isEqualTo(2);
    }

    private static MockHttpServletRequest forwarded(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        return request;
    }
}
