package com.clienthub.infrastructure.security.event;

import com.clienthub.domain.entity.User;
import com.clienthub.domain.repository.UserRepository;
import com.clienthub.infrastructure.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Component
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventListener.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT_LOG");

    private final UserRepository userRepository;

    public AuthenticationEventListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        Object principal = auth.getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            String email = userDetails.getEmail();
            String ipAddress = getClientIpAddress();
            
            auditLog.info("event=LOGIN_SUCCESS, user={}, ip={}, timestamp={}", 
                email, ipAddress, Instant.now());
            
            log.info("Successful authentication: user={}, ip={}", email, ipAddress);

            updateLastLoginInfo(userDetails.getId(), ipAddress);
        }
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = (String) event.getAuthentication().getPrincipal();
        String ipAddress = getClientIpAddress();
        String reason = event.getException().getClass().getSimpleName();
        
        auditLog.warn("event=LOGIN_FAILED, username={}, ip={}, reason={}, timestamp={}", 
            username, ipAddress, reason, Instant.now());
        
        log.warn("Failed authentication attempt: username={}, ip={}, reason={}", 
            username, ipAddress, reason);
    }

    @Transactional
    protected void updateLastLoginInfo(java.util.UUID userId, String ipAddress) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            user.setLastLoginIp(ipAddress);
            userRepository.save(user);
        });
    }

    private String getClientIpAddress() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            return "UNKNOWN";
        }

        HttpServletRequest request = attributes.getRequest();
        
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "UNKNOWN";
    }
}
