package com.market.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressesTest {

    @Test
    void clientKeyNeverResolvesHostnames() {
        assertThat(ClientAddresses.clientKey("random123.attacker.example")).isEqualTo("random123.attacker.example");
        assertThat(ClientAddresses.clientKey("999.10.20.30")).isEqualTo("999.10.20.30");
        assertThat(ClientAddresses.clientKey("10.1.2.3")).isEqualTo("10.1.2.3");
        assertThat(ClientAddresses.clientKey("2001:db8:1:2::7")).isEqualTo("20010db800010002::/64");
    }

    @Test
    void clientKeyIgnoresZoneId() {
        assertThat(ClientAddresses.clientKey("fe80::1%eth0")).isEqualTo("fe80000000000000::/64");
    }

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
}
