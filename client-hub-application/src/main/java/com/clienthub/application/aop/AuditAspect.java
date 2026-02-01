package com.clienthub.application.aop;

import com.clienthub.application.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);
    private final AuditService auditService;
    private final ExpressionParser parser = new SpelExpressionParser();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning(pointcut = "@annotation(logAudit)", returning = "result")
    public void logAuditActivity(JoinPoint joinPoint, LogAudit logAudit, Object result) {
        try {
            String ipAddress = getClientIp();

            String entityId = parseEntityId(joinPoint, logAudit.entityId(), result);

            auditService.log(
                    logAudit.action(),
                    logAudit.entityType(),
                    entityId,
                    null,    // oldValue
                    result,
                    ipAddress
            );

        } catch (Exception e) {
            logger.error("AOP Audit Error: Could not record audit log", e);
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
        }
        return "UNKNOWN";
    }

    private String parseEntityId(JoinPoint joinPoint, String spelExpression, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("result", result);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Object value = parser.parseExpression(spelExpression).getValue(context);
            return value != null ? value.toString() : "UNKNOWN";
        } catch (Exception e) {
            logger.warn("Failed to parse SpEL entityId: {}", spelExpression);
            return "UNKNOWN";
        }
    }
}