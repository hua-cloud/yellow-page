package com.example.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {
    // 接口文档页面 : http://localhost:8081/swagger-ui/index.html
    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info().title("后端接口文档").version("1.0.0"));
    }
    @Bean
    public GroupedOpenApi httpApi() {
        return GroupedOpenApi.builder()
                .group("http")
                .pathsToMatch("/**")
                .build();
    }
}
