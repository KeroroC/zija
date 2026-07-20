package com.zija;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI zijaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("知家 API")
                .version("1")
                .description("知家家庭物品管理系统 REST API"));
    }
}
