package com.clienthub.web.security;

import com.clienthub.infrastructure.security.JwtTokenProvider;
import com.clienthub.infrastructure.security.RateLimitFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(jwtTokenProvider);
        ReflectionTestUtils.setField(rateLimitFilter, "loginLimit", 5);
        ReflectionTestUtils.setField(rateLimitFilter, "registerLimit", 3);
        ReflectionTestUtils.setField(rateLimitFilter, "aiLimit", 10);
        ReflectionTestUtils.setField(rateLimitFilter, "generalLimit", 60);
    }

    @Test
    void shouldAllowRequestWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks");
        request.setMethod("GET");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockRequestExceedingLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks");
        request.setMethod("GET");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        ReflectionTestUtils.setField(rateLimitFilter, "generalLimit", 1);

        rateLimitFilter.doFilter(request, response, filterChain);

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, blockedResponse, filterChain);

        verify(filterChain, times(1)).doFilter(any(), any());
        assertEquals(429, blockedResponse.getStatus());
        assertEquals("60", blockedResponse.getHeader("Retry-After"));
    }

    @Test
    void shouldRateLimitDifferentEndpointsDifferently() throws Exception {
        // Test login
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRequestURI("/api/auth/login");
        request1.setMethod("POST");
        request1.setRemoteAddr("192.168.1.1");
        
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request1, response1, filterChain);

        Map<String, ?> buckets = getBuckets();
        assertTrue(buckets.containsKey("login:192.168.1.1"));
        
        // Test AI endpoint with JWT
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/ai/generate");
        request2.setMethod("POST");
        request2.addHeader("Authorization", "Bearer valid-token");
        request2.setRemoteAddr("192.168.1.1");
        
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.extractUserId("valid-token")).thenReturn(userId);

        rateLimitFilter.doFilter(request2, response2, filterChain);

        assertTrue(buckets.containsKey("ai:" + userId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> getBuckets() {
        return (Map<String, ?>) ReflectionTestUtils.getField(rateLimitFilter, "buckets");
    }
}
