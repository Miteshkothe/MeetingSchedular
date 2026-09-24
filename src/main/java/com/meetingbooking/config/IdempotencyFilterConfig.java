package com.meetingbooking.config;

import com.meetingbooking.idempotency.IdempotencyInterceptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdempotencyFilterConfig {

    @Bean
    public FilterRegistrationBean<IdempotencyInterceptor> idempotencyServletRegistration(IdempotencyInterceptor filter) {
        FilterRegistrationBean<IdempotencyInterceptor> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
