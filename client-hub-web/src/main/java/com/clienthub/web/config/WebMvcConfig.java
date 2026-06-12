package com.clienthub.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Value("${file.upload-dir:./uploads/attachments}")
    private String uploadDir;

    // Constructor injection - best practice
    public WebMvcConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")  // Only intercept API endpoints
                .excludePathPatterns(
                        "/api/health",
                        "/api/actuator/**",
                        "/api/swagger-ui/**",
                        "/api/v3/api-docs/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir);
        String uploadAbsolutePath = uploadPath.toFile().getAbsolutePath();
        registry.addResourceHandler("/uploads/attachments/**")
                .addResourceLocations("file:" + uploadAbsolutePath + "/");
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> attachmentSecurityFilter() {
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> registrationBean = new org.springframework.boot.web.servlet.FilterRegistrationBean<>();
        registrationBean.setFilter((request, response, chain) -> {
            jakarta.servlet.http.HttpServletResponse res = (jakarta.servlet.http.HttpServletResponse) response;
            res.setHeader("Content-Disposition", "attachment");
            res.setHeader("X-Content-Type-Options", "nosniff");
            chain.doFilter(request, response);
        });
        registrationBean.addUrlPatterns("/uploads/attachments/*");
        return registrationBean;
    }
}
