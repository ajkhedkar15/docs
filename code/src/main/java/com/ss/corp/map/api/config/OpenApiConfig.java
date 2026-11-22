package com.ss.corp.map.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and version shown in Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mapOpenApi() {
        return new OpenAPI().info(new Info()
                .title("myAuditPlan (mAP) API")
                .version("v1")
                .description("Backend for the Attribute Tagging screen"));
    }
}
