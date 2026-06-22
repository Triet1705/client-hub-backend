package com.clienthub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clienthub.common.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class TenantInterceptorTest {

    private final TenantInterceptor interceptor = new TenantInterceptor();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void preHandleUsesDefaultTenantWhenHeaderMissingInDevelopmentMode() {
        ReflectionTestUtils.setField(interceptor, "requireTenantHeader", false);

        boolean result = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertTrue(result);
        assertEquals("default", TenantContext.getTenantId());
    }

    @Test
    void preHandleRejectsMissingTenantHeaderWhenRequired() {
        ReflectionTestUtils.setField(interceptor, "requireTenantHeader", true);

        assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void preHandleRejectsInvalidTenantHeaderWhenRequired() {
        ReflectionTestUtils.setField(interceptor, "requireTenantHeader", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "bad tenant");

        assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
