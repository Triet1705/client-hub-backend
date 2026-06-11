package com.clienthub.web.config;

import com.clienthub.common.context.TenantContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Pattern INVOICE_STATUS_TOPIC = Pattern.compile("^/topic/invoices/(\\d+)/status$");

    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;

    private final com.clienthub.infrastructure.security.JwtTokenProvider jwtTokenProvider;
    private final com.clienthub.domain.repository.InvoiceRepository invoiceRepository;

    public WebSocketConfig(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.clienthub.infrastructure.security.JwtTokenProvider jwtTokenProvider,
            com.clienthub.domain.repository.InvoiceRepository invoiceRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(org.springframework.messaging.simp.config.MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        if (jwtTokenProvider != null) {
            registration.interceptors(new ChannelInterceptor() {
                @Override
                public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message, org.springframework.messaging.MessageChannel channel) {
                    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                    
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        authenticateConnect(accessor);
                        return message;
                    }

                    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        authorizeSubscribe(accessor);
                    }
                    return message;
                }
            });
        }
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor.getNativeHeader("Authorization"));
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new org.springframework.messaging.MessageDeliveryException("Unauthorized");
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            sessionAttributes = new java.util.HashMap<>();
            accessor.setSessionAttributes(sessionAttributes);
        }
        sessionAttributes.put("userId", jwtTokenProvider.extractUserId(token));
        sessionAttributes.put("role", jwtTokenProvider.extractRole(token));
        sessionAttributes.put("tenantId", jwtTokenProvider.extractTenantId(token));
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = INVOICE_STATUS_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new org.springframework.messaging.MessageDeliveryException("Unauthorized");
        }
        Object userIdValue = sessionAttributes.get("userId");
        Object roleValue = sessionAttributes.get("role");
        Object tenantIdValue = sessionAttributes.get("tenantId");
        if (!(userIdValue instanceof UUID userId) || !(roleValue instanceof String role) || !(tenantIdValue instanceof String tenantId)) {
            throw new org.springframework.messaging.MessageDeliveryException("Unauthorized");
        }

        Long invoiceId = Long.valueOf(matcher.group(1));
        TenantContext.setTenantId(tenantId);
        boolean allowed;
        try {
            allowed = "ADMIN".equals(role)
                    ? invoiceRepository.existsByIdAndTenantId(invoiceId, tenantId)
                    : invoiceRepository.existsAccessibleByIdAndTenantIdAndUserId(invoiceId, tenantId, userId);
        } finally {
            TenantContext.clear();
        }

        if (!allowed) {
            throw new org.springframework.messaging.MessageDeliveryException("Forbidden");
        }
    }

    private String extractBearerToken(List<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return null;
        }
        String token = authorizationHeaders.get(0);
        if (!token.startsWith("Bearer ")) {
            return null;
        }
        return token.substring(7);
    }
}
