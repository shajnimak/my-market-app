package ru.yandex.practicum.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import ru.yandex.practicum.payment.api.BalanceApi;
import ru.yandex.practicum.payment.api.PaymentsApi;

/**
 * Сервис платежей — сервер ресурсов OAuth2. Каждый запрос должен нести access-токен (JWT) сервера авторизации,
 * выданный по Client Credentials Flow. Spring Boot настраивает {@code ReactiveJwtDecoder} по свойствам
 * {@code spring.security.oauth2.resourceserver.jwt.*}: подпись проверяется ключами из JWK Set сервера авторизации,
 * а также срок действия, издатель ({@code iss}) и получатель ({@code aud} = {@code payment-service}).
 * Скоупы токена превращаются в полномочия {@code SCOPE_*}: просмотр баланса требует {@code payments.read},
 * списание — {@code payments.write}. Без токена ответ 401, без нужного скоупа — 403.
 * Сервис не хранит состояние между запросами: сессий, CSRF, формы логина и HTTP Basic нет.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String SCOPE_PAYMENTS_READ = "SCOPE_payments.read";
    public static final String SCOPE_PAYMENTS_WRITE = "SCOPE_payments.write";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, BalanceApi.PATH_GET_BALANCE).hasAuthority(SCOPE_PAYMENTS_READ)
                        .pathMatchers(HttpMethod.POST, PaymentsApi.PATH_MAKE_PAYMENT).hasAuthority(SCOPE_PAYMENTS_WRITE)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .build();
    }
}
