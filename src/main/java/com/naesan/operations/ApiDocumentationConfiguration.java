package com.naesan.operations;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "내산 API",
        version = "v0.1.0",
        description = "패스 발급·공유·검증·이전과 EVM 기술적 기록을 제공하는 API"
))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class ApiDocumentationConfiguration {
}
