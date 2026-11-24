package com.swetonyancelmo.encurt_url.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Encurt URL API")
                .version("v1")
                .description("API para encurtar URLs, redirecionar e consultar estatísticas de cliques."));
    }
}
