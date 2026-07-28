package com.clienthub.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void walksTrustedChainFromRightToLeft() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8,192.0.2.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 192.0.2.8");

        assertEquals("198.51.100.99", resolver.resolve(request));
    }

    @Test
    void rejectsMalformedForwardedChain() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "attacker.example");

        assertEquals("10.0.0.5", resolver.resolve(request));
    }
}
