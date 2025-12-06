package se.moln.orderservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("v1")
                        .description("Order endpoints"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer authentication")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .servers(List.of(
                        new Server()
                                .url("https://order-service-ismete-hxh3eqc6asa2ffhu.northeurope-01.azurewebsites.net")
                                .description("Production server (Azure)"),
                        new Server()
                                .url("http://localhost:8082")
                                .description("Local development server")
                ));
    }
}