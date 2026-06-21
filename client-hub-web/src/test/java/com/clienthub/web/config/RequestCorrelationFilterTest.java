package com.clienthub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void doFilterEchoesIncomingRequestAndCorrelationIds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "req-123");
        request.addHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER, "corr-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("req-123", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertEquals("corr-456", response.getHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER));
    }

    @Test
    void doFilterCreatesIdsWhenHeadersAreMissing() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertNotNull(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNotNull(response.getHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER));
    }
}
