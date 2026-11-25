package com.swetonyancelmo.encurt_url.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String MANAGEMENT_TOKEN = "managementToken";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Encurt URL API")
                        .version("v1")
                        .description("API para encurtar URLs, redirecionar e consultar estatísticas de cliques."))
                .components(new Components().addSecuritySchemes(MANAGEMENT_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token devolvido uma única vez na criação do link (campo managementToken).")));
    }
}
