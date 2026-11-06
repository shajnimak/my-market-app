package ru.yandex.practicum.payment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.payment.config.SecurityConfig;
import ru.yandex.practicum.payment.service.AccountService;

import java.util.Arrays;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Общая конфигурация веб-слоя: все контроллеры, правила доступа {@link SecurityConfig} и один мок сервиса счетов,
 * поэтому контекст {@code @WebFluxTest} создаётся один раз и переиспользуется наследниками.
 * Аутентификация по токену подменяется {@code mockJwt()}: проверяются правила доступа по скоупам,
 * а разбор настоящих JWT проверяет {@code PaymentApiSecurityIntegrationTest}.
 */
@WebFluxTest
@Import(SecurityConfig.class)
public abstract class AbstractControllerTest {

    @Autowired
    protected WebTestClient webTestClient;

    @MockitoBean
    protected AccountService accountService;

    /**
     * Клиент с JWT, у которого есть перечисленные полномочия (например, {@code SCOPE_payments.read}).
     */
    protected WebTestClient withAuthorities(String... authorities) {
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt.subject("market-app"))
                .authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new)));
    }
}
