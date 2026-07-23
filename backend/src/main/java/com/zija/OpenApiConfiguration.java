package com.zija;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）文档配置。
 *
 * <p>定义 API 文档的基本信息：标题为"知家 API"，版本号为 1，
 * 描述为"知家家庭物品管理系统 REST API"。
 * 生成的 OpenAPI 文档可通过 {@code /v3/api-docs} 和 {@code /swagger-ui.html} 访问。
 */
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
