package com.market.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class UntrustedProxyDetectorTest {

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
