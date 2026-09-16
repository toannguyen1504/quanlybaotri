package com.example.quanlybaotri.shared.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuditInterceptor audit;

    public WebConfig(AuditInterceptor a) {
        audit = a;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audit).addPathPatterns("/api/v1/**");
    }
}
