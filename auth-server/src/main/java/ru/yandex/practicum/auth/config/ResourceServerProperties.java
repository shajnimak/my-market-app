package ru.yandex.practicum.auth.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;

/**
 * Серверы ресурсов, зарегистрированные в сервере авторизации ({@code auth.resource-servers.<id>.scopes}).
 * Идентификатор сервера ресурсов попадает в claim {@code aud} токена, если клиенту выдан хотя бы один из его скоупов:
 * так сервер ресурсов проверяет, что токен выпущен именно для него.
 *
 * @param resourceServers идентификатор сервера ресурсов → его скоупы
 */
@Validated
@ConfigurationProperties(prefix = "auth")
public record ResourceServerProperties(Map<String, ResourceServer> resourceServers) {

    public ResourceServerProperties {
        resourceServers = resourceServers == null ? Map.of() : Map.copyOf(resourceServers);
    }

    /**
     * @param scopes скоупы, которые защищает сервер ресурсов
     */
    public record ResourceServer(@NotEmpty Set<String> scopes) {
    }
}
