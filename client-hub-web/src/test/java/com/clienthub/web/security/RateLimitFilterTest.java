package com.clienthub.web.security;

import com.clienthub.infrastructure.security.JwtTokenProvider;
import com.clienthub.infrastructure.security.RateLimitFilter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitFilterTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> bucketBuilder;

    @Mock
    private BucketProxy bucket;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(proxyManager, jwtTokenProvider);
        ReflectionTestUtils.setField(rateLimitFilter, "loginLimit", 5);
        ReflectionTestUtils.setField(rateLimitFilter, "registerLimit", 3);
        ReflectionTestUtils.setField(rateLimitFilter, "aiLimit", 10);
        ReflectionTestUtils.setField(rateLimitFilter, "generalLimit", 60);

        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
    }

    @Test
    void shouldAllowRequestWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks");
        request.setMethod("GET");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(bucket.tryConsume(1)).thenReturn(true);

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

        when(bucket.tryConsume(1)).thenReturn(false);

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
    }

    @Test
    void shouldRateLimitDifferentEndpointsDifferently() throws Exception {
        // Test login
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRequestURI("/api/auth/login");
        request1.setMethod("POST");
        request1.setRemoteAddr("192.168.1.1");
        
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        when(bucket.tryConsume(1)).thenReturn(true);
        rateLimitFilter.doFilter(request1, response1, filterChain);
        
        verify(bucketBuilder).build(ArgumentMatchers.argThat(bytes -> new String(bytes).startsWith("login:192.168.1.1")), any(Supplier.class));
        
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
        when(bucket.tryConsume(1)).thenReturn(true);
        
        rateLimitFilter.doFilter(request2, response2, filterChain);
        
        verify(bucketBuilder).build(ArgumentMatchers.argThat(bytes -> new String(bytes).startsWith("ai:" + userId)), any(Supplier.class));
    }
}
