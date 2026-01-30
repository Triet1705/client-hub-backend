package com.clienthub.infrastructure.config;

import com.clienthub.common.context.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("AsyncAudit-");

        executor.setTaskDecorator(new ContextCopyingDecorator());

        executor.initialize();
        return executor;
    }

    static class ContextCopyingDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            String currentTenantId = TenantContext.getTenantId();

            return () -> {
                try {
                    SecurityContextHolder.setContext(securityContext);
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                    TenantContext.setTenantId(currentTenantId);

                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                    RequestContextHolder.resetRequestAttributes();
                    TenantContext.clear();
                }
            };
        }
    }
}