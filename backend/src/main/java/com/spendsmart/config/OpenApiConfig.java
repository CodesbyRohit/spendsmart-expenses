package com.spendsmart.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI spendSmartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SpendSmart Expenses API")
                        .version("1.0.0")
                        .description("""
                                Session-cookie authenticated REST API. Sign in with POST /api/auth/login; the browser \
                                then sends the SPENDSMART_SESSION cookie. State-changing requests must echo the \
                                XSRF-TOKEN cookie in an X-XSRF-TOKEN header."""))
                .components(new Components().addSecuritySchemes("session", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("SPENDSMART_SESSION")))
                .addSecurityItem(new SecurityRequirement().addList("session"));
    }
}
