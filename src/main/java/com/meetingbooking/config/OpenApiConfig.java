package com.meetingbooking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Meeting Room Booking API")
                        .description("""
                                Production-quality Meeting Room Booking backend with:
                                - Recurring meetings (Daily, Weekly, Monthly)
                                - Timezone & DST-safe recurrence generation
                                - PostgreSQL GiST exclusion constraints for concurrency
                                - Rolling 12-month horizon expansion
                                - Redis caching with resilient fallback
                                - JWT authentication with ROLE_USER / ROLE_ADMIN
                                """)
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
