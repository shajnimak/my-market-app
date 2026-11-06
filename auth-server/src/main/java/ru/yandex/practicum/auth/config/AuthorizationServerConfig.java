package ru.yandex.practicum.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Сервер авторизации для межсервисного взаимодействия по Client Credentials Flow.
 * <p>
 * Клиенты регистрируются свойствами {@code spring.security.oauth2.authorizationserver.client.*}
 * (Spring Boot создаёт из них {@code RegisteredClientRepository}), ключ подписи RSA для JWT и JWK Set
 * Spring Boot генерирует при старте. Пользовательских входов (authorization code, форма логина) нет:
 * открыты только протокольные эндпоинты сервера авторизации, всё остальное запрещено.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {

    /**
     * Эндпоинты сервера авторизации: {@code /oauth2/token}, {@code /oauth2/jwks}, {@code /oauth2/introspect},
     * {@code /oauth2/revoke}, {@code /.well-known/oauth-authorization-server}. Клиент аутентифицируется
     * своими {@code client_id}/{@code client_secret} фильтрами самого сервера авторизации.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Всё, что не относится к протоколу OAuth2, недоступно.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Добавляет в {@code aud} access-токена идентификаторы серверов ресурсов, чьи скоупы выданы клиенту.
     * Сервер авторизации по умолчанию кладёт в {@code aud} только {@code client_id}.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> resourceServerAudienceCustomizer(ResourceServerProperties properties) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            Set<String> authorizedScopes = context.getAuthorizedScopes();
            Set<String> audience = new LinkedHashSet<>(context.getClaims().build().getAudience());
            properties.resourceServers().forEach((resourceServerId, resourceServer) -> {
                if (resourceServer.scopes().stream().anyMatch(authorizedScopes::contains)) {
                    audience.add(resourceServerId);
                }
            });
            context.getClaims().audience(List.copyOf(audience));
        };
    }
}
